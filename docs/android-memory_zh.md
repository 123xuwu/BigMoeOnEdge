# 从引擎视角看 Android 的内存回收

为什么一个超过 RAM 的流式加载引擎，在 Android 手机上会丢掉它的工作集；有哪些可用手段（几乎没有）；
以及在相信任何相关理论之前应该先核对哪些数字。本文在 12 GB 测试手机上测得
（Android 16、内核 6.12、11.4 GB RAM、12 GB swap，**未 root**），运行的是 gpt-oss-120b；
其中机制是通用的，而常量属于这台设备。

下文所有内容都来自设备上的 `/proc`，或者厂商公开的 GPL 内核源码。凡是无法实时读取的值，文中都会注明——
其中有几个关键值只有 root 才能读取。

## 问题的形态

回收**不是一个需要从中恢复的事件，而是一种持续状态**。它在引擎*解码的过程中*就发生，而不只是空闲时。
在一次应用内对话中，每 4 秒采样一次：

```
t=4    RssAnon 2.02 GB   swap 463 MB     <- 缓存全部驻留
t=12   RssAnon 1.86 GB   swap 620 MB     <- 内核正在拿走它
t=20   RssAnon 1.57 GB   swap 917 MB
t=44   RssAnon 1.46 GB   swap 1.02 GB
t=68   RssAnon 2.02 GB   swap 468 MB     <- 引擎把它缺页读回
t=88   RssAnon 1.88 GB   swap 596 MB     <- 然后又失去它
```

真正让这一轮对话付出代价的，是这种来回震荡，而不是某一次回收。

## 空闲内存，以及为什么 95 MB 并不是危机

水位线（watermark）作用于 **MemFree**——即物理上空闲的页帧——而不是 `MemAvailable`。
Linux 故意让 MemFree 保持在接近零的位置：不用的 RAM 就是浪费的 RAM，所以多余的内存都会变成页缓存。
因此一台健康的手机*就生活在*它的水位线上，kswapd 被唤醒是正常现象，不是病态。

本设备 Zone Normal 的水位线（`/proc/zoneinfo`），相对于 11.4 GB RAM：

| 水位线 | 页数 | 字节 | 影响 |
|---|---|---|---|
| min | 5,792 | **22.6 MiB** | 低于此值：**直接回收**——发起分配的线程同步执行回收 |
| low | 24,262 | **94.8 MiB** | 低于此值：kswapd 被唤醒 |
| high | 42,732 | **166.9 MiB** | kswapd 回收到此为止，然后停止 |

两个间隔都是 18,470 页；由于 `gap = max(min/4, managed × wsf / 10000)` 且 managed = 2,841,569，
可推算出 **`watermark_scale_factor` ≈ 65，而原生默认值是 10**——这里的 kswapd 比原生版本有 6.5 倍
的迟滞性，会一直追着 167 MiB 的空闲内存跑。

真正出问题的信号**不是**“kswapd 在运行”，而是 `/proc/vmstat` 中的 `allocstall_*` 和 `pgsteal_direct`：
直接回收会被计入*你自己的*线程，表现为不可中断睡眠，就发生在 `llama_decode` 内部，而那里没有任何
tok/s 计数器能看到它。本机全系统统计：`pgsteal_kswapd` 270,019,486 对 `pgsteal_direct` 80,282,940——
**所有回收中有 23% 是直接回收**。

## 两种内存，以及为什么我们这种更贵

| | 回收成本 | 恢复成本 |
|---|---|---|
| **干净的文件后备内存**（mmap 的权重、页缓存） | **免费**——直接丢弃，无 I/O | 从文件重新读取 |
| **匿名内存**（我们的专家缓存缓冲区） | 压缩 + 写入 zram，可能还要写回 NAND | 读取 + 解压 |

专家缓存*是 gguf 中已经存在的字节的一份拷贝*，而且恰好是内核回收时必须写出去的那种形式。
本设备的 `pswpout`：**36.8 M 页 ≈ 开机以来写入 140 GB**。
更糟的是，Q4_K_M 权重熵很高，因此 zram 几乎以未压缩形式存放它们：换出既耗 CPU，
又几乎释放不出任何空间。*（推断——`/sys/block/zram0/mm_stat` 只有 root 可读；
用 root 对比 `huge_pages` 与 `pages_stored` 即可确认。）*

这是 O_DIRECT 设计未被注意到的代价。O_DIRECT 换来了大规模顺序读取，避免 4 KiB 的缺页风暴——
这确实真实有效，值得这么做——但它同时把免费的驱逐变成了有代价的驱逐。任何后续设计都应权衡这两半。

## 命中率决定了内核是否会保留你的缓存

LRU 只有在**第二次引用**时才会把一页从 *inactive* 提升到 *active*——而一次缓存命中*就是*第二次引用。
所以命中率不仅仅是“避免了多少次闪存读取”：它字面上就是内核用来决定你的内存是否值得留下的信号。

| 模型 | 文件 | 缓存 | 命中率 | tok/s | 内核的结论 |
|---|---|---|---|---|---|
| Qwen3-30B-A3B | 18.5 GB | 4000 MiB | **77.1%** | 4.57 | 被访问两次 → active → 受保护 |
| Gemma-4-26B-A4B | 17.0 GB | 4000 MiB | **82.9%** | 4.76 | 同上 |
| gpt-oss-120b | 58.5 GB | 3000 MiB | **13.4%** | 1.15 | 被访问一次 → inactive → 被换出 |

而且命中率是几何性质，不是调参结果：

```
gpt-oss-120b: 56.8 GB experts / (36 layers × 128) = 12.3 MB per expert slot
              3000 MiB cache covers  5.2% of the bank  ->  13% hit
Qwen3-30B:    16.3 GB experts / (48 layers × 128) =  2.6 MB per expert slot
              4000 MiB cache covers 24.5% of the bank  ->  77% hit
```

gpt-oss 的专家库大 3.5 倍，切片体积也大 4.6 倍，因此同样的缓存只覆盖其 5 分之一不到的面积。
用接近均匀的路由去覆盖一个 128 专家库的 5%，命中率大约就是 13%。**对这个模型来说内核是对的：
它被要求持有 2 GB 而引擎自己只会碰它们一次。** 覆盖比例低于某个阈值的缓存不仅不划算——
它实际上有害：它会驱逐与之竞争的 dense 权重，并制造大量分配抖动去唤醒 kswapd。

## 厂商把天平压向了对匿名内存不利的一侧

`/dev/memcg/.../memory.swappiness` 读出来是 **100**，这个数字是个幌子。厂商的 zram 调优模块
**已被确认加载**（`/proc/modules`），并挂接了 `android_vh_set_swappiness`，
在回收路径*内部*覆盖了该 sysctl（依据厂商公开的 GPL 内核源码）：

```c
static int g_direct_swappiness = 60;
static int g_swappiness = 160;
static void zo_set_swappiness(void *data, int *swappiness) {
    if (current_is_kswapd()) *swappiness = g_swappiness;      /* 160 */
    else                     *swappiness = g_direct_swappiness; /* 60 */
}
static void zo_set_inactive_ratio(void *data, unsigned long *inactive_ratio, bool file) {
    if (file) *inactive_ratio = min(2UL, *inactive_ratio);
    else      *inactive_ratio = 1;
}
```

swappiness 是 0–200 的比值，表示 swap I/O 与文件系统换页的相对成本假设
（见 [vm.rst](https://docs.kernel.org/admin-guide/sysctl/vm.html)）；100 表示两者等价。
因此 **kswapd 扫描匿名内存的压力是文件缓存的 4 倍**，这就是为什么我们 2 GB 的缓存被丢进 zram，
而 4.9 GB 的页缓存却安然无恙。匿名内存的 `inactive_ratio = 1` 还额外迫使它约 50% 被降级——
从构造上就被停在回收最先吃掉的链表上。实时数据佐证：Active(anon) 459 MB 对
Inactive(anon) 3,123 MB（87% 处于 inactive）。

> ⚠️ **关键但未经验证。** `160` 来自厂商 5.4 的源码；该模块在这台 6.12 设备上确认已加载，
> 但它的 `/sys/module/<module>/parameters/vm_swappiness` 只有 root 可读，**尚未读取**。
> 这是高置信度的推断，不是实测。有 root 的话，一条命令即可确认。

注意这种不对称性：kswapd（160）偏好抢走我们的匿名页，而我们自己的直接回收（60）会去抢文件缓存——
一旦我们分配得太快，就会驱逐自己的 dense 权重。

## 没有什么能保护匿名内存。所有手段，以及它们为何不可用

| 手段 | 在本机的结论 |
|---|---|
| `mlock` / `mlockall` / `MAP_LOCKED` | **不可用**——`RLIMIT_MEMLOCK` = **65536 字节，软硬限制都是**（`/proc/<pid>/limits`；AOSP `init.rc` 如此设置）。2 GB 缓存中的 64 KB = 0.003% |
| 用 `setrlimit` 提高限制 | **不可用**——提高*硬*限制需要 `CAP_SYS_RESOURCE`；应用既没有它也没有 `CAP_IPC_LOCK` |
| cgroup `memory.min` / `memory.low` | **不存在**——本机 memcg 是 **v1**，位于 `/dev/memcg`（0700 root:system，`limit_in_bytes` 无限制）；那些旋钮仅 v2 支持，而 cgroup2 没有内存控制器 |
| `memory.reclaim` | 仅 v2，且 AOSP 本来就不用 |
| `/proc/<pid>/reclaim` | **不存在**——这是高通补丁，从未进入主线；AOSP 在 Android 12 已改用 `process_madvise()` |
| MGLRU 相关旋钮 | `CONFIG_LRU_GEN=y` 且默认启用，但 `/sys/kernel/mm/lru_gen/enabled` = `0x0000`——**运行时被厂商关闭**。实际生效的是经典 active/inactive LRU |
| `MADV_HUGEPAGE` | 无效果——THP 为 `[never]` |
| `oom_score_adj`、前台服务 | 只影响**被杀**的选择。内核回收基于 LRU，与进程无关，从不参考它们 |
| `onTrimMemory` | 只是通知，而且已大幅废弃——自 API 34 起应用收不到大多数级别的通知 |
| Memory Advice API | **已废弃**；只会发出警告，从不保住任何内容驻留 |
| `MAP_POPULATE` | 预缺页，**不锁定**。只影响加载速度 |
| **`MADV_COLD` / `MADV_PAGEOUT`**（5.4+） | **唯一真正可用的手段**——无需特权。不能阻止回收，但*可以选择受害者*：主动把自己的冷尾页交出去，让 kswapd 找到便宜的目标，从而减少直接回收的卡顿 |
| **通过 `AHardwareBuffer` 使用 dma-buf** | **唯一真正起作用的手段**——这些页会保持锁定，因为设备随时可能从它们 DMA，且不需要任何 capability。可以获得与匿名内存相同的读取速度，把 dense 权重放进去后解码 **+17.9%**（[2026-07-21](bench-data/2026-07-21-pinned-dense-ab/findings.md)）。每个缓冲区上限 **2047 MiB**：即使分配能达到 4 GiB 的格式上限，`AHardwareBuffer_lock` 在 2^31 字节时仍会返回 `EINVAL`。以 `--dense-weights pinned` 形式提供，默认关闭——见下文 |

**lmkd 从不回收——它只杀进程——而且在这台设备上它按设计保持沉默。** 只要
`SwapFree >= swap_free_low_percentage`（10%）它就直接返回；而我们处于 70–79%。
**12 GB 的 swap 正是没有任何东西保护我们的原因**：从 lmkd 的角度看，还有空闲 swap 的机器就是健康的机器。
厂商的 Osense/“涅槃”机制会追着 `purposeFreeMB=390`，但只杀 `type=cached` 的应用，
碰不到 top-app 进程；它那些 `kill cnt=0` 的日志行，说明系统在 kswapd 来找我们之前，
已经先耗尽了*其他*受害者。

作为对照：iOS 提供 `os_proc_available_memory()`（真正的每进程预算）以及提高它的 entitlement。
**Android 两者都没有。** 所调查的任何设备端运行时（llama.cpp、MLC、MediaPipe/LiteRT、ONNX Runtime、
ExecuTorch）都没有在 Android 上锁定权重，也没有公布“保持在 RAM 的 X% 以下”这类规则；
llama.cpp 的 `--mlock` 在这里因上述原因失败，而 ExecuTorch 明确把 Android 映射为 `NoMlock`。

## dma-buf 例外，以及它没有解决的问题

以上一切讨论的都是内核*被允许*拿走的内存。有一种分配它不允许：**dma-buf**，
它的页在缓冲区生命周期内都被锁定，因为设备随时可能从其中 DMA。应用可以通过格式为 `BLOB` 的
`AHardwareBuffer` 获得它——不需要 capability、不需要 root，而且完全绕开 `RLIMIT_MEMLOCK`，
因为它根本不是 `mlock`。

在测试设备上实测的属性（[数据](bench-data/2026-07-21-pinned-memory/findings.md)）：

| 属性 | 数值 |
|---|---|
| 相对 `anon` 的吞吐，长生成 | **+17.9%**（见下） |
| 相对匿名内存的读取带宽 | **1.00×**——两个簇上分别为 30.4 GiB/s 和 45.2 GiB/s，4 线程时 59.0，与 anon 的差距都在 0.5% 以内 |
| `CPU_READ_OFTEN` 与 `CPU_READ_RARELY` | 无差别；不需要费力去提示 |
| 单缓冲区最大可用大小 | **2047 MiB**——`AHardwareBuffer_lock` 在 2^31 字节返回 `EINVAL`，这是有符号 32 位边界，尽管分配能达到 4 GiB 的格式上限 |
| 分配成本 | 约 7–11 GiB/s，即加载时一次性几百毫秒 |

“带宽只是普通水平”这个结论才是关键，因为它原本是该想法一出生就死的合理途径：
gralloc 会按每次分配决定缓冲区是否可被 CPU 缓存，而不可缓存的映射读取速度会等于或低于闪存带宽——
那就会让锁定权重比重新缺页读取它们还慢。

`--dense-weights pinned` 把 dense 权重放在那里。它就是 `anon` 加一处替换——缓冲区来自
`pio::pinned_alloc` 而不是堆——因此与 `anon` 做 A/B 只改变一个变量；而且当平台没有这种分配时它会拒绝启动，
而不是回退，否则比较就会悄悄变成一种模式与它自己对比。

**应用内实测，长生成**（[2026-07-21](bench-data/2026-07-21-pinned-dense-ab/findings.md)）：

| | `anon` | `pinned` |
|---|---|---|
| 解码 tok/s | 2.588 | **3.053（+17.9%，置信区间不相交）** |
| `dense_resident_frac` | 0.848 | **1.000** |
| 每 token 主缺页数 | 265 | 257——*相等* |
| `compute_ms` | 298 | **241** |
| `io_ms`、`stall_ms`、缓存命中率 | \- | *均无变化* |
| swap | 562 MiB | 294 MiB |

免回收的性质成立：在每一个 pinned 运行中都是精确的 1.000，包括最小值在内。

**但这个收益并不是原先预测的那个，而且这个修正比收益本身更重要。**
主缺页数是*相等的*。`anon` 本来就已经让 dense 权重不碰闪存——它没有阻止的是内核把其中约 15% 拿进
**zram**。一页被回收进 zram 后再次访问并不会产生主缺页；它是次缺页加一次解压。
这份成本不出现在任何 I/O 计数器或缺页计数器里，于是落在 `compute_ms` 上——而那个值是残差，
不是对算术运算的测量。这就解释了为什么整个差值都出现在那里，而其他指标纹丝不动。

所以：**`anon` 保护 dense 权重不被读自闪存；`pinned` 还额外保护它们不被换入 zram**，
而 `anon` 一直悄悄付出的这笔代价约值 18%。原先担心的取舍——锁定内存饿死专家缓存——
在这个规模下没有出现：dense 集合（约 1.6 GiB）相对 3000 MiB 的缓存预算很小，
命中率精确到小数点后都相同。

有两点保留意见使它**默认关闭**：在决定性的那一对比较中 `pinned` 先运行，顺序效应无法排除
（反向的一对尚待补测），而且这只涉及一台设备、一个模型、一种配置。

更普遍的教训比这个开关更长命：`compute_ms` 一直在吸收 zram 的解压成本，因此任何早先得出的
*“这个区间是计算受限的”*结论都值得重新审视。

### 一个比 RAM 还大的 dense 张量

上述每种模式都假定 dense 集合装得下。`qwen4exp`（Qwen3.8-Flash-Next）用一个张量打破了这一假定：
`per_layer_token_embd`，一张 51B 参数的 n-gram 嵌入表，IQ4_NL 下约 28.8 GB，
计算图每个 token 要从中聚集十六行。任何模式都装不下它，而每种方式的失败方式都不同：
`anon` 会申请一个让操作系统直接杀掉进程的分配，`pinned` 会撞上 2047 MiB 的 dma-buf 上限，
`warm` 会在页缓存里扫过 28.8 GB 而页缓存在扫描过程中就把它们驱逐了，
驻留传感器则会报告一个永远不可能驻留的 dense 集合。因此引擎在任何模式之前先应用一条规则：
大于内核 `MemAvailable` 的 dense 张量会被留置不用。它保持 mmap，退出 warm 扫描和传感器统计，
自动缓存预算也不再为一次不会发生的转换预留空间。会有一行 stderr 说明被留置的是什么。

被留置的映射还会获得 `MADV_RANDOM`。默认预读每次缺页会读入几百 KiB 的窗口，
这对整体读取的权重是对的，对行聚集来说则错两个数量级：相邻页永远不会被访问，
却会占用与专家缓存竞争的页缓存。加上该建议后，一次缺页只映射一页。
每个 token 就是十六次 4 KiB 读取，不到一个解码步骤的 1%；剩下的成本在预填充阶段，
那里每个提示词 token 都在计算线程上触发自己的行缺页。尚未在设备上实测。

按大小设界是有意为之，访问模式并不是判据：一个*装得下*的行聚集表仍保持其模式，
因为在预填充中按需缺页它的代价比加载时一次读完更高
（保持 mmap 的 `token_embd` 实测解码 −16%，[PR #135](https://github.com/Helldez/BigMoeOnEdge/pull/135)）。

## 为什么恢复被回收的页不可能取胜

对匿名内存调用 `MADV_WILLNEED` 确实会把它们换回来——但 `read_swap_cache_async` 把它们放到
**inactive** 链表上（`folio_add_lru`，`mm/swap_state.c`）；没有任何东西标记它们被访问过。
只有真正的第二次访问才会提升为 active。因此批量恢复交给内核的是一堆位于*驱逐队列前端*的页，
而在 swappiness 160 且 `inactive_ratio = 1` 的情况下，下一轮回收几乎立刻到来。

实测：恢复 1.76 GB 花了 6.4 秒，内核在 8 秒内就把它拿了回去，那一轮对话仍然只有 0.3 tok/s。
**这 8 秒是 LRU 的性质，不是代码的性质。**

推论才是有用的部分：恢复只有在这些页随后被*命中*时才有价值——这使它的价值成为命中率的函数，
并预测它恰好只对高命中率的模型有帮助，而那些模型本来也不需要它。

## 在相信这里任何理论之前要核对的清单

- 读取 `MemFree` **和** `MemAvailable` **和** `Cached`——空闲约 100 MB 是正常的，不是症状。
- 拆分 RSS：`/proc/<pid>/status` → `RssAnon` 与 `RssFile` 与 `VmSwap`。`VmSwap` 只能看到
  匿名那一半；**文件后备的回收对它不可见**，而它在本机要大 27 倍
  （`workingset_refault_file` 114.8 M 对 `workingset_refault_anon` 4.2 M）。
- `VmHWM` 能告诉你一个进程是否真的持有过你以为它持有的东西——它能识别出“这是一个新进程”，
  而在单纯的 RSS 采样中，这与“这个进程被剥空了”看起来完全一样。
- 用速率而不是计数器：`/proc/vmstat` 的总量是自开机以来的。要在窗口内取差值。
- **在应用里测量。** 应用里的引擎在回答结束后约 5 秒内从 3.5 GiB 掉到 3.5 MiB；
  而一个 adb 会话花了 4 分钟才丢掉其中一半，第二次 adb 运行几乎什么都没丢。
  本仓库所有 bench 脚本都是单次运行且从不停留空闲，因此没有一个能看见这一类 bug。
  要看见它，需要两轮对话并在中间留一段计时的空闲间隔，并在间隔期间持续采样 `/proc`。
- 小心 `pgrep -f <name>`：执行该命令的 shell 会匹配到自己的命令行。一个 3.8 MB 的“引擎”其实是你的 `sh`。