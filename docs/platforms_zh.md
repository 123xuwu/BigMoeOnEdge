# 平台支持

什么能在哪里运行、每个平台做到哪一步、以及各自还欠什么。本页只是摘要；
每个单元格背后的证据都在它链接的页面里，而没有实测支撑的单元格会直接说明，不会暗示有。

最后复核于 2026-10-19，对应发行版 0.32.0。

## 如何阅读这些表格

| 状态 | 含义 |
|---|---|
| **已实测**（Measured） | 由本项目在真实硬件上运行过，数字在链接页中。 |
| **已发布**（Shipped） | 由 CI 构建并附加到发行版，或存在于发行版构建中。 |
| **已编译**（Built） | 能在 CI 中或用本仓库脚本编译通过，但尚未在此处运行。 |
| **待补**（Owed） | 已设计或部分实现，缺失的工作在下方列出。 |
| **未构建**（Not built） | 本仓库没有任何构建包含它。 |
| - | 不适用于该平台。 |

“已实测”是关于该类某一台机器的陈述，从不代表整个平台。这些机器都是有意做泛化描述的。

## 操作系统

| | Android（arm64） | macOS（arm64） | Linux（x86_64、aarch64） | Windows（x86_64、arm64） | iOS |
|---|---|---|---|---|---|
| **引擎与 `bmoe-cli`** | 已发布，在 APK 内 | 已发布 | 已发布 | 已发布 | 已编译，编译进 harness 应用中 |
| **`bmoe-server` 与 Web 界面** | 已发布，在 APK 内作为应用的子进程 | 已发布：在 `BigMoeOnEdge.app`（`.dmg`）中，也在 CLI 归档中 | 已发布：在 AppImage 中，也在 CLI 归档中 | 已发布：在按用户安装的安装器中（`-npu` 构建仅在其归档中），也在 CLI 归档中 | 编译进应用，运行在自己的线程上（无 Web 界面：应用本身就是界面） |
| **原生应用** | 已实测：Compose 应用，服务器的客户端 | 已运行：`bmoe-desktop`，服务器之上的 Web 界面装在原生窗口中（[desktop-app.md](desktop-app.md)） | 已编译：基于 WebKitGTK 4.1 的 `bmoe-desktop`；尚欠一次桌面运行 | 已编译：基于 WebView2 的 `bmoe-desktop`；尚欠一次运行 | 已编译：SwiftUI 应用，在其独立进程中作为服务器的客户端；仅在模拟器中运行过 |
| **规划器（`--auto`）** | 已实测，在应用的加载路径中 | 在一台笔记本上实测 | 已编译，未在 Linux 硬件上运行 | 待补：一次有记录的运行 | 由贡献者在一台 12 GB 手机上通过 harness 运行过（#235）；其 dense 策略在 #304 中修正 |
| **独立进程中的设备探测** | 是 | 是 | 是 | 是，已编译；待补一次运行 | -（没有第二个进程） |
| **CI** | 引擎交叉编译并打包 APK，在 PR 和 tag 上运行 | 编译并做冒烟测试，在 PR 和 tag 上运行 | 构建、字节一致性闸门和 UI 测试，每次 push 运行 | 仅编译，在 PR 和 tag 上运行；arm64 在 ARM64 runner 上编译并做冒烟测试 | 为模拟器编译应用与 harness，并测试应用的客户端包，在 PR 和 tag 上运行；harness 由 release 工作流打包 |
| **发行产物** | CI 构建的签名 APK，以模块形式包含 Hexagon、Vulkan 和 OpenCL 后端 | `.dmg`（临时签名，未公证）与 CLI 归档，含 Metal | AppImage 与 CLI 归档，两种架构，含 Vulkan；x86_64 另有 CUDA 与 HIP 包 | 安装器 `.exe`（未签名）与 CLI 归档，两种架构，含 Vulkan；arm64 额外含面向 Adreno GPU 的 OpenCL；x86_64 提供 CUDA 包。全部列入已签名的 `feed.json` | 用于模拟器的 harness 应用，未签名，由 CI 构建 |
| **硬件运行** | 已实测：一台 12 GB 手机，骁龙级别，UFS 4.x | 已实测：一台 16 GB Apple silicon 笔记本 | 待补：只有闸门，没有吞吐运行 | 已实测：一台 x86 笔记本，8 核、16 GB、NVMe。arm64：待补，从未在骁龙笔记本上运行 | 由贡献者实测：一台 12 GB 手机，#304 之前以 1.2 tok/s 流式运行 35B-A3B |

来源：服务器在各平台上的情况见 [application-framework.md](application-framework.md)，
规划器与隔离探测见 [hardware-planning.md](hardware-planning.md)，
实测机器见 [community-benchmarks.md](community-benchmarks.md)，
应用与 harness 见 [`examples/ios`](../examples/ios/README.md)。

## 各平台的计算后端

引擎不实现自己的任何后端；每个都是 llama.cpp 的，通过公开的设备注册表接入。
[backends.md](backends.md) 列出了所固定子模块携带的全部十八个后端，以及每次使用设备时对它们的要求。
本表是其中已经在此处构建或运行过的子集。

| 后端 | Android | macOS | Linux | Windows | 目前的作用 |
|---|---|---|---|---|---|
| **CPU** | 已实测 | 已实测 | 由 CI 闸门实测 | 已实测 | 在所有平台上负责解码、预填充和专家流式加载 |
| **Hexagon**（高通 NPU） | 已实测，随 release APK 发布 | - | - | 已编译，在独立的 Windows ARM64 归档（`-npu`）中，DSP 库在 CI 中签名；Windows 只有在用户手动完成一次设置（包含测试签名）后才会运行它（[npu-windows.md](npu-windows.md)）；从未在骁龙笔记本上运行 | 预填充在 NPU 上，解码在 CPU 上（[npu-prefill.md](npu-prefill.md)） |
| **Metal** | - | 已实测，在所有 macOS 构建中开启 | - | - | 预填充在 GPU 上，解码在 CPU 上（[npu-prefill.md](npu-prefill.md)） |
| **OpenCL** | 在骁龙级别手机上实测，位于 release APK：在预填充宽度上很快但结果不正确，因此规划器拒绝使用它；7 月时手机上 GPU 的 dense 权重损失了 27% | - | 未构建 | 随 arm64 归档发布，含 Adreno 内核；从未运行 | 暂无成效：在手机上因正确性问题被拒绝；在笔记本上由探测决定（[npu-prefill.md](npu-prefill.md)） |
| **Vulkan** | 在骁龙级别手机上实测，位于 release APK：在预填充宽度上不比 CPU 核心更快，未启用；在一台 2020 年的手机上其驱动让探测崩溃，被隔离探测所容纳 | 未构建 | 已发布，发行归档中的模块；从未运行 | 已发布，等同 Linux；从未运行 | 暂无成效：在实测手机上不是收益（[npu-prefill.md](npu-prefill.md)） |
| **CUDA** | - | - | 面向 x86_64 作为独立包发布，含它链接的 NVIDIA 运行时和 cuBLAS，应用会安装到自己的数据文件夹（AppImage 亦然），或解压到 `bmoe-cli` 旁边；在租用的 24 GB 显卡上用源码构建实测 | 面向 x86_64 作为 Linux 发布 | 承担拥有独立内存的设备所承担的一切角色（[backends.md](backends.md)） |
| **HIP**（AMD ROCm） | - | - | 面向 x86_64 作为包发布，仅模块本身：应用安装到 ROCm 7 运行时可加载的位置；在租用的 192 GB 数据中心卡上用源码构建实测，模型常驻（[bench-data/2026-10-28-rented-rocm](bench-data/2026-10-28-rented-rocm/README.md)） | 未构建 | 同 CUDA |
| **SYCL** | - | - | 未构建：用工具链构建并放到二进制旁边的模块会像已发布的模块一样加载 | 同 Linux | 无 |
| **RPC** | - | - | 闸门用的测试装置 | - | 前端无法选择 |

每个发行版的形态都相同：引擎，以及旁边的每个后端一个模块、每个指令集层级一个 CPU 模块，
在启动时加载（[backends.md](backends.md)）。APK 在 Hexagon 旁边携带 Vulkan 和 OpenCL，
Linux 与 Windows 归档携带 Vulkan，Windows arm64 归档还携带 OpenCL（其 `-npu` 孪生版本另有 Hexagon），
macOS 归档携带 Metal。机器缺少某模块所需驱动库时该模块不会被加载，
因此同一个归档既服务于有 GPU 的笔记本也服务于没有 GPU 的服务器，
规划器则找出实际注册了哪些设备。

CUDA 和 HIP 是“放在归档里”这一规则的例外：带 NVIDIA 运行时和 cuBLAS 的 CUDA 模块大约是其余部分的二十倍，
而 HIP 需要系统级安装 ROCm，因此 x86_64 发行版各自作为独立的包提供
（`bmoe-pack-cuda-<tag>-<target>`、`bmoe-pack-hip-<tag>-<target>`），
桌面应用会把它安装到自己的数据文件夹中使其生效，引擎从那里加载（[desktop-app.md](desktop-app.md)）；
解压到 `bmoe-cli` 旁边时，其文件也会像其他模块一样加载。

在 GPU 而非 CPU 核心上计算流式专家，在每个平台都只完成了一半：设备被测量了，
但流式加载器自己的内存还没有交给它
（[roadmap.md](roadmap.md#hardware-planning--what-is-measured-and-what-is-still-owed)）。

## 各平台还欠什么

- **Android。** 在 release APK（唯一带 Hexagon 后端的构建）上重跑 NPU 预填充：
  0.30.0 和 0.31.0 的手机运行都用了仅 CPU 的构建，而最后一次在全后端 APK 上启用 NPU 的加载
  终止于分配器（#264）。在同一 APK 上跑 GPU 预填充，让 Vulkan 和 OpenCL 与 NPU 及 CPU 核心对比。
  设置中逐值的来源标注，让读者能看到规划设定了什么、什么与默认值不同（[screens.md](screens.md)）。
- **macOS。** `.dmg` 完成公证，以及能自我更新的应用（#385）。在 Mac 上运行桌面应用并启用其 Metal 模块，
  在每个发行版前后各一次。
- **Linux。** 在真实硬件上的吞吐运行，可由社区协议提供
  （[community-benchmarks.md](community-benchmarks.md)）。一块独立 GPU，用于规划器的容量拟合、
  预填充设备和图宽规则。
- **Windows。** 在有 GPU 的机器上运行独立进程中的设备探测（现已编译）；缓存锁定、驻留采样、
  其 CI 任务中的字节一致性闸门、一次有记录的规划器运行，以及安装器签名（#385）。
  跟踪于 [#234](https://github.com/Helldez/BigMoeOnEdge/issues/234)。
- **iOS。** 基于共享框架的应用已能在模拟器中构建并运行，发行版携带了对应的 harness。
  一位贡献者在用免费 Personal Team 签名的设备上运行过 harness（#235），
  在其中读取到的规划已在 #304 中修正；尚欠的是该规划在设备上的一次运行，然后是应用在设备上运行。
  跟踪于 [#235](https://github.com/Helldez/BigMoeOnEdge/issues/235)。
- **所有平台。** 规划器尚欠的测量，以及将其预测与每次运行实测结果对比的闭环，
  跟踪于 [#237](https://github.com/Helldez/BigMoeOnEdge/issues/237)。

## 保持本页准确

任何改动到单元格的变更都要在同一个 PR 中更新本页，与本仓库其他文档一致。
每个发行版都会对照发行说明重读本表。无法由链接页面支撑的单元格会写成待补，而不是写成已完成。