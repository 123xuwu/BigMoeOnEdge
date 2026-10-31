package io.bigmoeonedge.app.data

/**
 * The settings form in Chinese.
 *
 * The engine describes its tunables once (core/src/params.cpp) and every front-end renders that
 * table: the CLI's `--help`, an English settings form, this one. Those strings are the engine's
 * and stay English — a knob's name is also what `--help` prints and what a plan decision is
 * written against, so translating them at the source would translate the CLI too.
 *
 * This file therefore translates at the app's edge, keyed by the parameter's stable `key` (the long
 * flag without its dashes, which never changes for an existing knob). A key with no entry falls
 * back to the engine's English, so a knob added upstream shows up in English rather than
 * disappearing — the same rule [Params] follows for a field an older server does not send.
 *
 * Group titles are keyed by the engine's stable group name (`generation`, `cache`, …), never by the
 * English label, so re-wording a title upstream cannot silently lose a translation.
 */
object ParamsZh {

    /** A parameter's short name. */
    private val labels: Map<String, String> = mapOf(
        "model" to "模型文件",
        "n-predict" to "最大生成长度",
        "think" to "思考模式",
        "ctx-size" to "上下文长度",
        "threads" to "计算线程数",
        "threads-batch" to "预填充线程数",
        "trial-threads" to "待测线程数",
        "trial-window" to "测量窗口 token 数",
        "chatml" to "按对话轮次包装",
        "temp" to "温度",
        "top-p" to "Top-p",
        "seed" to "随机种子",
        "moe-stream" to "专家流式读取",
        "device-cache-mb" to "设备端专家缓存",
        "expert-host-buffer" to "专家副本存入设备主内存",
        "io-threads" to "读取通道数",
        "o-direct" to "直接 I/O",
        "overlap" to "I/O 与计算重叠",
        "io-two-wave" to "分两波发起读取",
        "trial-overlap" to "自动测量重叠收益",
        "release-mmap" to "加载后解除映射",
        "cache-mb" to "专家缓存大小",
        "cache-floor-mb" to "缓存：保留给系统的内存",
        "cache-ceil-mb" to "缓存：上限",
        "routing-record" to "记录路由以供规划",
        "record-unstreamed" to "非流式运行时也记录",
        "dense-weights" to "稠密权重驻留方式",
        "ubatch" to "批处理宽度",
        "ctx-checkpoints" to "上下文检查点",
        "checkpoint-min-step" to "检查点最小间隔",
        "row-stream" to "行流式表",
        "row-stream-mb" to "行流式窗口",
        "gpu-layers" to "放在设备上的层数",
        "dense-on-device" to "稠密权重放到设备",
        "dense-mapped" to "保持映射的表",
        "tensor-overrides" to "张量放置规则",
        "device-split" to "各设备的层数分配",
        "device-list" to "设备后端",
        "devices" to "计算设备",
        "prefill-device" to "预填充设备",
        "prefill-best-effort" to "预填充设备可降级",
        "prefill-verify" to "校验预填充设备",
        "prefill-min-tokens" to "预填充设备最小宽度",
        "prefill-slots" to "预填充专家槽位",
        "prefill-slots-on-demand" to "槽位与缓存共享内存",
        "prefill-loaders" to "预填充加载线程数",
        "prefill-routed" to "预填充按路由读取",
        "prefill-routed-full" to "整层读取阈值",
        "decide" to "决策请求",
        "decide-prefix-cache" to "决策前缀缓存",
        "decide-probe" to "决策探测输出",
        "prefetch" to "时序预取",
        "predict-prefetch" to "预测式预取",
        "predict-spec-max" to "预测缺失的读取数",
        "spec" to "推测解码",
        "draft" to "草稿宽度",
        "mtp-head" to "MTP 头文件",
        "mtp-head-on-devices" to "MTP 头放到设备",
        "mtp-p-min" to "MTP 置信度下限",
        "ngram-min-match" to "N-gram 最短匹配",
        "ngram-max-match" to "N-gram 最长匹配",
        "n-expert-used" to "激活专家数",
        "drop-cold-experts" to "丢弃冷专家",
        "drop-renorm" to "丢弃后重新归一化",
        "drop-in-prefill" to "预填充时也丢弃",
        "expert-substitute" to "专家替换",
        "route-ahead" to "提前路由",
        "gate-mirror" to "提前路由使用 int8 门控",
        "predict-log" to "预测探测日志",
        "llama-debug" to "llama.cpp 调试日志",
        "load-all" to "加载全部专家",
        "cache-pin" to "锁定缓存内存",
        "cache-brake" to "内存压力下减速",
        "cache-pin-fit" to "缓存适配锁定范围",
        "force-cache" to "强制缓存大小",
        "prefetch-sync" to "同步预取",
        "top-k" to "Top-k 截断",
        "cache-type-k" to "Key 缓存类型",
        "cache-type-v" to "Value 缓存类型",
    )

    /** A parameter's explanation, in the same words the English form uses. */
    private val helps: Map<String, String> = mapOf(
        "model" to "要加载的 gguf 文件。模型被拆分成多个分片时，填第一个分片。",
        "n-predict" to "每次回答生成的 token 数上限。",
        "think" to "渲染对话模板时开启推理，使推理模型输出它的 thinking 通道。关闭（默认）则从源头抑制推理。",
        "ctx-size" to "上下文长度，单位为 token。KV 缓存与计算缓冲都按它预留，所以上下文越长，占用的内存越多，能从专家缓存那里分到的就越少。",
        "threads" to "计算线程数。",
        "threads-batch" to "预填充使用的线程数。预填充受算力限制、能随核心数增长，而流式解码大多在等闪存。0 表示与计算线程数相同。",
        "trial-threads" to "在第一次回答时，与计算线程数对比测量的解码线程数，填一到两个，用逗号分隔：缓存稳定后解码窗口会在它们之间交替测量，只有当某个值明显更快时才替换计算线程数。不改变输出；若结果被采纳，规划器会写入该项。",
        "trial-window" to "参数测量中每个计时窗口内的解码 token 数。太小的值只适合测试：一个 token 的窗口测到的就是单个 token 的噪声。",
        "chatml" to "用模型家族自己的对话轮次格式包装 prompt。对话类前端总是开启；关闭时 prompt 作为纯文本发送。",
        "temp" to "采样温度。0 或更低保持贪心解码（确定性）；大于 0 则启用 top-k → top-p → 温度的采样链。",
        "top-p" to "采样时的核采样（nucleus）截断，取值范围 (0, 1]。",
        "seed" to "采样的随机数种子。填 4294967295 表示每次运行随机取种子。",
        "moe-stream" to "每个 token 只从闪存流式读取被路由到的专家。关闭后，引擎就是跑在 mmap 上的原生 llama.cpp：只能作为基线，而不是本引擎。",
        "device-cache-mb" to "实验性：把流式读取的专家放在层所在的设备上计算，用该设备内存中这么多 MiB 的缓存来支撑。0 表示关闭。需要层已放在该设备上，且专家仍保留在主机侧。",
        "expert-host-buffer" to "实验性：在常驻专家之上再加一层设备端专家缓存时，把专家的一份副本放在该设备自己的主内存（其后端的内存）中，这样它能从可锁定的区域更快地上传。代价是在内存里再占一份专家大小的空间。留空表示关闭。",
        "io-threads" to "并行的专家读取通道数，包含发起调用的线程本身。1 为串行基线。",
        "o-direct" to "专家读取时绕过页缓存（O_DIRECT / FILE_FLAG_NO_BUFFERING）。",
        "overlap" to "把异步专家读取与 FFN 计算重叠起来，而不是阻塞等待。输出逐字节相同。需要本构建启用了对应的 expert-ready 钩子。",
        "io-two-wave" to "先发布某一层第一次投影所需的读取，再提交其余部分，让读取通道更早开始工作。需要开启重叠与缓存；尚待设备端 A/B 验证。",
        "trial-overlap" to "在第一次回答时，把另一种重叠设置与当前设置放在交替的解码窗口中对比，只有当它明显更快时才采用。不改变输出；若结果被采纳，规划器会写入该项。需要所有层都在主机侧。",
        "release-mmap" to "加载完成后，若已没有任何读取经过该文件，就解除对模型文件的映射。在 Windows 上，一个活跃的映射会让流式读取的并发读变成串行。需要稠密权重设为 anon 或 pinned。",
        "cache-mb" to "LRU 专家缓存的大小，单位为 MiB；填 auto 表示在加载时按可用内存自动确定一次。0 表示关闭缓存。低于一个 token 工作集大小的预算会导致频繁抖动。",
        "cache-floor-mb" to "缓存设为 auto 时：要留给系统其余部分使用的内存。",
        "cache-ceil-mb" to "缓存设为 auto 时：预算的上限（0 表示不限）。",
        "routing-record" to "记录本模型在前 N 个 token 上路由到的专家，让下一次 --auto 规划能按本模型真实的命中率来确定缓存大小。不改变输出。0 表示关闭。",
        "record-unstreamed" to "非流式运行的路由也一并记录，每个 token 在每层 MoE 处设一次屏障，直到记录写满。当某个选择取决于该记录时，--auto 会开启它。",
        "dense-weights" to "稠密（非专家）权重如何保持常驻。mmap 交给内核管理；warm 在加载时预热页缓存（模型能整体装下时最好）；anon 读入我们自己的缓冲区，这样回收压力落在 zram 而不是闪存上；pinned 则是放在内核完全不可回收的内存中，前提是平台提供这样的存储（旧名 ahwb 仍然可用）。",
        "ubatch" to "一次性计算的最大图宽度（0 表示与上下文等宽）。计算缓冲按它预留，所以调小可以把内存让给专家缓存，代价是预填充变慢；解码不受影响。",
        "ctx-checkpoints" to "无法在任意位置截断的上下文状态副本（循环状态、滑动窗口），按 llama.cpp 服务端的方式保存。若后续请求的 prompt 在该状态内部与缓存不一致，会从差异之前最新的那份副本重新开始，而不是从头开始。每份副本都占内存；上下文可在任意位置截断的模型不会保存任何副本。0 表示关闭。",
        "checkpoint-min-step" to "分块预填充同一个 prompt 时，两个上下文检查点之间的最小距离。每个 prompt 结尾总是会保存一份。0 表示不设最小值。",
        "row-stream" to "对于计算图中只按行取用的稠密表（如 token 嵌入表），从闪存读取而不是常驻内存。哪些表符合条件由计算图决定，而不是靠一张名字清单。",
        "row-stream-mb" to "行流式表的常驻窗口大小。",
        "gpu-layers" to "存放在计算设备上的层数，按 llama.cpp 的填充方式从顶层开始计数：这些层的专家常驻设备，不再流式读取。0 表示全部留在主机侧。硬件规划器会依据 llama.cpp 自身的内存容量拟合结果来设置它。",
        "dense-on-device" to "把稠密权重放到与该主机共享内存的加速器上，把专家留给流式读取。实测在手机 GPU 上会变慢；仅作为可选的实验开关保留。",
        "dense-mapped" to "无论 --dense-weights 如何设置，都保持内存映射的稠密张量，按名字指定，用 ; 分隔：这些是计算图只按行取用的表，硬件规划器评估后认为不值得常驻。由规划器设置；留空则整个稠密集合保持原样。",
        "tensor-overrides" to "llama.cpp 容量拟合器给出的逐张量放置规则：对张量名的正则表达式，用 ; 分隔，每一条匹配到的张量都留在主机侧。由硬件规划器设置；留空则每个张量都按层数分配决定放置位置。",
        "device-split" to "每台设备各承担多少层，写成 NAME:LAYERS 对，用 , 分隔，顺序与 llama.cpp 容量拟合器列出设备的顺序一致。由硬件规划器设置；留空则交给 llama.cpp 按可用内存分配。",
        "device-list" to "llama.cpp 可以使用的设备，按名字指定，用 , 分隔。当两个后端驱动同一张卡时（CUDA 与 Vulkan、HIP 与 Vulkan），--auto 会列出实测更快的那一个；留空则交给 llama.cpp，保留最先注册的那个。",
        "devices" to "llama.cpp 可以使用的计算设备。auto 表示：有层放在设备上时使用全部设备，否则只用 CPU。因为一个注册了却没被使用的设备仍会接管计算图节点，每接管一个都是一次跨设备边界。预填充设备单独指定，不受此项影响。",
        "prefill-device" to "在这个 ggml 设备上运行宽预填充计算图（例如 HTP0、MTL0），解码仍留在 CPU。开启流式时，专家通过一个双层 arena 送达该设备。不能与行流式同时使用；开启推测解码时，校验宽度不能小于 --prefill-min-tokens。留空表示关闭。",
        "prefill-best-effort" to "如果预填充设备无法就绪，就在 CPU 上做预填充，而不是让整个加载失败。当 --auto 自己选择了该设备时，会开启此项。",
        "prefill-verify" to "在正式采用某个预填充设备之前，先用一段固定的短文本分别在 CPU 和该设备上预填充，只有当它在几乎所有位置都给出相同的下一个 token 时才保留该设备。加载时会多花三次短预填充的时间。",
        "prefill-min-tokens" to "发给预填充设备的最小片段宽度；更短的尾段留在 CPU 上运行。",
        "prefill-slots" to "预填充设备同时持有的流式专家层数。2 表示在计算当前层时就去读取下一层；1 表示逐层等待，能少占一层的内存。",
        "prefill-slots-on-demand" to "预填充设备的专家槽位只在预填充期间占用，其余时间把内存让给专家缓存。缓存在解码时仍保有完整预算；每次预填充会先淘汰槽位所需的空间，结束后再重新建立。",
        "prefill-loaders" to "开启流式时，从闪存填充预填充设备层槽位的线程数。解码的读取通道仍由读取通道数设置决定。",
        "prefill-routed" to "同时开启预填充设备与流式读取时：只读取当前计算图实际路由到的专家（由上一张图预测，并在每个路由节点处补全），而不是每层的全部专家。两种方式输出完全相同。",
        "prefill-routed-full" to "若某一层路由到的专家占比超过这个比例，下一层就整层读取。取值范围 (0, 1]。",
        "decide" to "配合 --session：接受决策请求（在一次预填充中从候选列表里选出一项，不做解码）。",
        "decide-prefix-cache" to "配合决策：在一次请求的前缀处理完后保留模型状态，当下一个请求的前缀是其延展时直接恢复。auto 表示在预填充开销随 token 数增长时开启。",
        "decide-probe" to "配合决策：为每次决策追加输出每层路由到的专家，以及在各层出口处读到的答案（JSONL 格式）。",
        "prefetch" to "推测性地提前读取上几个 token 在接下来 K 层路由到的专家。需要开启缓存。0 表示关闭。",
        "predict-prefetch" to "依据下一层门控在当前层输入上的预测来提前读取专家，并保护被预测为常驻的专家不被淘汰。需要开启缓存；不能与时序预取同时使用。",
        "predict-spec-max" to "预测式预取每层允许读取的预测缺失数（0 表示只做常驻保护，不额外读取）。",
        "spec" to "先起草一段后续内容，再用一次更宽的解码来校验。mtp 使用模型自带的预测头（需要 nextn 块）；ngram 则在 prompt 和已生成内容中查找最近出现过的 token 序列。需要贪心解码。",
        "draft" to "每个校验批次起草的 token 数。",
        "mtp-head" to "仅用于 MTP：一个单独存放模型 MTP 头的 GGUF（llama.cpp 的转换脚本加 --mtp 即可生成），用于模型文件本身不含该头的情况。留空则使用模型内部的头。",
        "mtp-head-on-devices" to "使用独立头文件时的 MTP：把头加载到模型各层所在的设备上，而不是主机。当设备计算专家更快、且头能放进专家缓存旁边时，硬件规划器会这样设置（头会占用一部分缓存空间）。",
        "mtp-p-min" to "仅用于 MTP：当预测头的最佳候选概率低于此值时停止起草（0 表示总是起草满宽度）。",
        "ngram-min-match" to "仅用于 N-gram：允许起草的最短匹配 token 串长度。",
        "ngram-max-match" to "仅用于 N-gram：查找匹配时考虑的最长后缀长度。",
        "n-expert-used" to "覆盖每个 token 路由到的专家数（top-k）。调小会更快，但会改变输出。0 表示采用模型自带的值。",
        "drop-cold-experts" to "跳过某个路由到的专家：若它未命中缓存，且权重低于路由总权重的 F × (1/top-k)，就丢弃它。与缓存状态相关，会改变输出且不可复现。需要开启缓存。0 表示关闭。",
        "drop-renorm" to "丢弃专家后对剩余权重重新归一化，使路由保持原有的权重总量。",
        "drop-in-prefill" to "在预填充阶段也执行丢弃。默认关闭：冷缓存下这样做的代价很高。",
        "expert-substitute" to "在确定一次解码路由之前，按 L × 该 token 的分值跨度提高每个常驻专家的分数并重新排序：分差接近时倾向于选择已在内存中的专家。需要开启缓存。0 表示关闭。",
        "route-ahead" to "把解码路由确定下来，采用同一次前向计算中提前 N 层的预测结果，这样预取就永远不会落空。会改变输出。与前两种预取及推测解码互斥。0 表示关闭。",
        "gate-mirror" to "配合提前路由：用一个 int8 版的路由门控（只占四分之一字节数）来给预测打分，而不是用门控自身的权重。auto 会在加载时在本机对两者计时，取更快的那个。",
        "predict-log" to "测量每层的路由有多少能提前一层得知。不会改变任何被读到的结果，但每层会多一次 GEMV 开销：不适合作为基准测试运行。",
        "llama-debug" to "让 llama.cpp 自身的 DEBUG 日志输出到 stderr。关闭时这些日志会被丢弃：每次加载模型约 1200 行。用于调试 llama.cpp 本身，而不是本引擎。",
        "load-all" to "每个 token 都读取全部专家：用于全量扫描的 A/B 基线。",
        "cache-pin" to "在平台允许的前提下，把缓存的专家锁定在内存中，使内核无法换出或压缩它们。被锁定的内存会从系统其余部分的可用量中扣除。",
        "cache-brake" to "当系统在运行过程中开始回收缓存内存时，把专家缓存减半：判断依据是 token 出现停顿，且能确认有缓存页已不在内存中。缓存只会缩小，且不会低于一个 token 的工作集；内存没有压力时不会做任何事。",
        "cache-pin-fit" to "当平台中途停止锁定缓存时，把预算缩到实际锁定住的大小，而不是把其余部分继续当作普通内存持有。在内存压力会导致压缩的场景中有意义；当 --auto 以这种方式确定缓存大小时会开启。",
        "force-cache" to "允许把缓存预算设到低于一个 token 工作集的异常区间。",
        "prefetch-sync" to "仅供测试：在返回前于求值线程上完成每一次推测性读取。",
        "top-k" to "采样时按 Top-k 截断候选（0 表示关闭该阶段）。",
        "cache-type-k" to "为上下文保留的注意力 key 的元素类型，与 llama.cpp 同名参数一致。默认的 q8_0 相比 llama.cpp 的 f16 让长上下文的内存减半，q4_0 则减到四分之一，代价是位数越少质量损失越明显。省下的内存会归专家缓存。f16 是无损的。",
        "cache-type-v" to "为上下文保留的注意力 value 的元素类型，与 llama.cpp 同名参数一致。量化类型需要 flash attention，llama.cpp 会在后端支持时启用；不支持时回退到 f16。",
    )

    /** A group's section title, keyed by the engine's stable group name. */
    private val groups: Map<String, String> = mapOf(
        "model" to "模型",
        "generation" to "生成",
        "sampling" to "采样",
        "streaming" to "专家流式读取",
        "prefill" to "预填充",
        "cache" to "专家缓存",
        "memory" to "内存与常驻",
        "prefetch" to "预取",
        "speculation" to "推测解码",
        "lossy" to "质量取舍",
        "diagnostics" to "诊断",
    )

    /** A group's one line of help, keyed the same way. */
    private val groupHelps: Map<String, String> = mapOf(
        "model" to "要运行的模型文件。带模型列表的前端在列表里选择即可。",
        "generation" to "回答本身以及计算它所用的设置：长度、思考、上下文和线程。",
        "sampling" to "默认是贪心且确定性的；温度设为大于 0 才会启用采样。",
        "streaming" to "解码某个 token 时，被路由到的专家如何从存储中读取。",
        "prefill" to "prompt 如何计算：在哪个设备上算，以及它的专家如何读取。",
        "cache" to "把读过的专家留在内存里，这样下一个 token 就不必再读一次。",
        "memory" to "非专家的权重放在哪里，以及哪些设备持有层。",
        "prefetch" to "在某个 token 需要之前就先把专家读进来。",
        "speculation" to "起草 token 并在一次解码中校验；贪心校验能保证输出不变。",
        "lossy" to "更快，但回答会不一样：这里的每一项都会改变输出。",
        "diagnostics" to "测试、探测和 A/B 基线；任何一项都不是用来提速的设置。",
    )

    /** Values that mean something other than their number, keyed by the engine's English label. */
    private val specials: Map<String, String> = mapOf(
        "same as the compute threads" to "与计算线程数相同",
        "greedy" to "贪心",
        "off" to "关闭",
        "random per run" to "每次随机",
        "no cap" to "不设上限",
        "as wide as the context" to "与上下文等宽",
        "no minimum" to "不设最小值",
        "all on the host" to "全部在主机侧",
        "retention only" to "仅保护常驻",
        "always the full width" to "总是满宽度",
        "the model's own" to "模型自带值",
    )

    /** A choice's label, keyed by the choice's stable `value` (what the flag accepts and stores). */
    private val choices: Map<String, String> = mapOf(
        "auto" to "自动",
        "all" to "全部",
        "cpu" to "仅 CPU",
        "on" to "开启",
        "off" to "关闭",
        "none" to "关闭",
        "mtp" to "MTP 头",
        "ngram" to "N-gram 查表",
    )

    fun label(key: String, fallback: String): String = labels[key] ?: fallback

    fun help(key: String, fallback: String): String = helps[key] ?: fallback

    fun group(name: String, fallback: String): String = groups[name] ?: fallback

    fun groupHelp(name: String, fallback: String): String = groupHelps[name] ?: fallback

    fun special(english: String, fallback: String): String = specials[english] ?: fallback

    /** A choice's label, looked up by its stable value; an unknown value keeps its English. */
    fun choice(value: String, fallback: String): String = choices[value] ?: fallback
}
