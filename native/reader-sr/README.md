# CopyManga Neo：waifu2x 原生阅读器接入

本阶段基于 CMP `098cb6b65005370edda90e86807712bfd85fc321`、前一原型 `07b7db8ff16558d14ba48712e91e5508923789a9`，独立分支 `prototype/cmp-native-sr`。既有解码、缩放、原图缓存与下载策略保留。真实 Linux CPU 推理已通过应用适配器；Android arm64 和 Windows x64 已构建，运行验证范围见 [验证记录](VALIDATION.md)。没有执行 GitHub Actions、推送、PR、合并或发布。

## 产品行为与平台边界

阅读器附加设置中的「超分辨率 2×」默认关闭，状态限本次阅读会话，不修改现有设置/备份格式。自动模式先对实际 GPU 做模型推理自检；失败可选择 CPU 回退。CPU 模式不创建 Vulkan 实例；GPU 模式没有通过自检就保留原图，不假装 CPU 是 GPU。GPU 自检包含小样本 CPU 对照，因此 GPU 模式也需 CPU 能正常加载该模型。GPU 自检通过不保证大页、全部驱动或长期运行可靠。

不按 Qualcomm、MediaTek、AMD、Intel、NVIDIA 品牌判断。已打包 Android arm64、Linux x64、Windows x64；运行时按 OS/ABI 查找资源，再测试 CPU/Vulkan。没有资源、架构不匹配、模型摘要不符、内存不足、非正常 EXIF、动画、超大或过长图片均保留原图。Web/iOS 为明确不可用实现；macOS、Linux ARM、Windows ARM64、Android x86 此次未提供已验证包。构建脚本支持部分额外目标，但不能视为已经适配验证。NPU 不在范围内。

原有轻度锐化实现与默认值保持不变，仍见 [第一阶段说明](../../prototypes/reader-enhancement/README.md)。超分输出不写入原图缓存、不替换下载文件，不发送到任何远程推理服务。

## 数据流与隔离

```text
原有 Coil 原图成功显示
  → ReaderSrSession.select(SrPage(真实单页 key, 原始 source))
  → 原图就绪 + 开关开启 + 阅读器处于 resumed
  → 从原有 Coil disk cache / 本地文件读取编码原图（不发新网络请求）
  → 元数据检查、原始字节 SHA-256、预算检查
  → ReaderSrCoordinator 单任务 / 取消并等待旧任务退出
  → NativeReaderSrBackend 验证固定模型与运行时
  → DesktopSrWorker 子进程 或 AndroidSrWorker 专用服务进程
  → ncnn / waifu2x cunet 2×、noise=-1、tile=32、TTA=false、1线程
  → 校验 CSR1 尺寸与完整字节数 → 有界流式 sRGB RGBA8 PNG
  → 独立编码结果 LRU → 仅匹配真实单页 key 的图片显示
```

commonMain 只定义模型身份、预算、取消、状态和会话，不引用 JNI、AGSL、Vulkan 或 ncnn。共享 JVM 代码实现模型校验和有界文件协议；平台源码负责图像解码与进程生命周期。

当前只处理当前单页，没有猜测双页配对或预处理整章。协调器既有接口最多接收两张邻页，但产品接线不提交邻页。后续双页任务可提供两个真实单页 ID，不能以 spread 位置替换缓存/任务身份。翻页、离开阅读器、后台、关闭开关取消任务；全 JVM 互斥保证不同窗口也不并行推理。不同 OS 应用实例不共享并发门，但使用文件锁避免互删活跃临时任务。

桌面每次 probe/run 启动子进程，退出码/信号变成后端失败，取消强制终止并 reap 后才释放门。Android native 仅由非导出 `:reader_sr` 服务装载；主 Application 在此进程跳过普通应用图初始化。Messenger/Binder death 报告失败，STOP/unbind 令服务结束自身进程；3秒内未确认死亡则隔离后端、拒绝新任务。JNI 的异常处理不能捕获 SIGSEGV。独立进程减少主阅读器被 native 信号直接终止的风险，但不能保证驱动/系统故障、整机 OOM、系统崩溃提示都被隔离。Android IPC、系统重建/杀进程行为尚需设备验证。

自动 GPU 失败后在新 worker 中尝试一次 CPU；GPU 隔离到会话结束。显式 GPU 不回退。模型校验失败或 CPU 失败保留原图。每任务60秒超时，自检20秒上限。编码和文件 I/O 不在 UI 线程，取消在这些同步小阶段结束后检查；不能声称每一条机器指令均可立即取消。

## 预算、缓存与长图限制

- 原编码文件最多16MiB；输入长边<=4096、面积<=3,145,728像素。
- 固定 tile32 + padding18，单任务单线程。2× 输出宽高各翻倍，像素及对应 RGBA 内存4倍。
- 输入+输出 RGBA 预算 `20 × inputPixels`，另预留96MiB模型/分块工作集；桌面总准入预算192MiB；Android取192MiB、应用 memoryClass/2、当前可用RAM/8中最小值。
- 预算为准入估算，不是 OS 强制内存上限；原图渲染、JVM/ART、GPU驱动和系统缓存另占内存。旧实验tile32约61MiB worker RSS；不能直接外推 Android/GPU。
- 神经网络按小块计算，但输入/输出完整 RGBA 向量仍驻留 worker。尚无 region decode + 流式 tile sink，所以超限长图直接原图，不能宣称全尺寸长图超分完成。
- 有界PNG按行压缩，避免额外完整输出位图；最多16MiB编码结果，失败不会留半成品。
- 编码结果缓存最多3项且总16MiB；键包含真实页ID、原始字节SHA、尺寸/方向、权重+图digest、模型/运行时版本、2×/noise/tile/TTA、执行模式、回退设置和sRGB策略。源地址只是读取位置。
- 作业临时文件位于应用缓存，finally删除；下次运行清理>60秒且未锁定的遗留任务。模型与原图不进入清理范围。下载源只复用原缓存，缓存被驱逐时原图继续阅读。

## 来源、许可与可复现构建

官方 [waifu2x-ncnn-vulkan](https://github.com/nihui/waifu2x-ncnn-vulkan) `20250915@a86cfb043e6482b5f08778a114df9b5faf6e73b7`；CPU修复 `3b82de7877bd840475d78fbab24b9bb7dfee781d` 包含在 `waifu2x-cpu.patch`。ncnn `c4193aadbbb56582aa87b1850dd3d98fb8fd936d`，glslang `8cd77a808d0bffa442ae9462d5e3a8141892ba5a`。构建脚本核对提交及补丁内容，模型取该官方仓库的 `models/models-cunet`：

- `scale2.0x_model.bin` SHA256 `d82ed30207fd07e1c16726a9ca838ce44e6e25a105b0278c7ca681516ee0e18e`
- `scale2.0x_model.param` SHA256 `ccc3b2f1c9fad829ecdda40cac8dc853fcddf14917170efba888b256fbf9074a`

waifu2x 仓库根 MIT 覆盖该仓库交付内容，但没有独立模型卡许可声明；不推断训练数据权利。ncnn BSD-3-Clause及其第三方说明、glslang许可证、Android NDK/Windows静态C++运行库随包保留。`NOTICE.txt`及平台许可文件来自固定源码/工具链。模型和二进制不提交到项目Git；本地构建bundle可显式放入应用包。没有运行时下载、自更新或静默安装外部程序。

Linux需JDK21及C++17/CMake工具链，Android使用NDK r30 `30.0.16248370`（API31、arm64、16KiB ELF段对齐），Windows当前交叉构建使用官方 llvm-mingw `20261020` UCRT。工具链必须显式准备，不由Gradle下载。首次无 `--source` 会从官方固定tag拉取源码/submodule，网络失败可改用已验证离线checkout。

```sh
python3 native/reader-sr/build.py --platform linux-x64 --source /path/to/waifu2x-cpu-debug
python3 native/reader-sr/build.py --platform android-arm64 --source /path/to/waifu2x-cpu-debug --ndk /path/to/ndk/30.0.16248370
LLVM_MINGW_ROOT=/path/to/llvm-mingw python3 native/reader-sr/build.py --platform windows-x64 --source /path/to/waifu2x-cpu-debug --toolchain native/reader-sr/mingw-toolchain.cmake
./gradlew :app:assembleDebug -PreaderSrBundle=/absolute/path/to/native/reader-sr/build/bundle
./gradlew :desktopApp:packageUberJarForCurrentOS -PreaderSrBundle=/absolute/path/to/native/reader-sr/build/bundle
./gradlew :desktopApp:packageUberJarForCurrentOS -Pcopymanga.desktopTarget=windows-x64 -PreaderSrBundle=/absolute/path/to/native/reader-sr/build/bundle
```

Windows产物是需JDK21的便携JAR，不是安装器；Linux本机链接glibc/libstdc++，需核对目标发行版，不保证任意旧系统可运行。没有`readerSrBundle`的普通构建仍可阅读，超分入口明确不可用。工具链和最终文件摘要记录在本轮报告/证据包。

## 验证与设备清单

真实CPU集成测试经打包resource、摘要校验、原生子进程和PNG编码，使用四类项目合成样张：小字、网点、细线、彩页。另测子进程SIGSEGV不退出JVM、取消/reap、GPU失败模拟回退、模式隔离、坏输出、源变化、模型篡改、任务清理和编码上限。模拟GPU失败测试不是GPU实测。小图指标不代表整页或目标平板性能。

优先真机：骁龙8 Gen2平板、用户称“8E”的手机（暂按8 Elite，需确认机型）；再选择一款MediaTek ARM64设备。记录型号、Android/API、RAM、Vulkan驱动、GPU自检结果，CPU/GPU/自动三模式及禁止CPU回退。桌面矩阵覆盖AMD/Intel x86 CPU、兼容核显/独显与NVIDIA Vulkan。每平台测同批样张的文字变形、halo、网点丢失、细线断裂/接缝；当前tile32与大tile像素并非一致。

必须实测：连续快速翻页、取消/后台/重进、离线缓存失效、模型/输出损坏、服务或子进程被杀、低内存、60秒超时、30分钟发热/耗电、原图首帧和翻页流畅度。记录峰值PSS/RSS/GPU内存、分位耗时，而非只报均值。Android服务崩溃隔离、Windows进程与Unicode路径、GPU真推理都尚未运行验证；Apple跳过，Wasm只编译回退。

潜在合并冲突：`ReaderScreen.kt`、`ReaderContent.kt`是唯一已有阅读器接线文件；`app/build.gradle.kts`、`shared/build.gradle.kts`、AndroidManifest、CopyMangaApplication、proguard以及架构文档可能与其他任务同时变更。请逐块合并，不能覆盖双页或UI任务。
