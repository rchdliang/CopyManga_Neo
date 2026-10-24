> 当前普通打包、按需模型与锐化入口以 [SR-BUILDING](../../docs/SR-BUILDING.md) 为准；以下保留原阶段记录。

# CMP 阅读器增强独立原型

**阶段更新：**真实 waifu2x 已接入 Android/桌面平台适配，Linux CPU 已出图；默认关闭。当前架构、构建和验证边界见 [native 接入说明](../../native/reader-sr/README.md)。以下保留 `07b7db8` 第一阶段评估记录，其中“尚未接入/后端不可用/CPU崩溃未解决”是历史状态，已由新阶段结果替代。

此分支保留 `CMP@098cb6b65005370edda90e86807712bfd85fc321` 的解码/缩放修复，只连接可选锐化。超分协调器及样张工具是独立实验，**尚未连接生产阅读器，也没有在应用中提供真实 AI 超分**。不修改 Android 分支 UI，不推送、开 PR、发布或合并。

## 核对基线与现有数据流

2026-10-23 通过 `git ls-remote --symref origin HEAD 'refs/heads/*'` 核对：默认 `CMP` 为上述提交，另有 `Android@9a0da27ad74528169000c021734a8bc2f54d64ec`；远端没有 master。原云端 checkout 是旧 `work@5499335`，因此另建 `prototype/cmp-reader-enhancement` worktree。未从旧 Android README 推断平台能力。仓库树中未发现 AGENTS.md 或 `.agents/skills`；工作区 `.agents` 为空。已读 README、docs/ARCHITECTURE.md、docs/BUILDING.md 与平台构建配置。

- CMP 模块：`shared` 的 commonMain/Android/Desktop/iOS/Wasm；`app`、`desktopApp`、`iosApp` 是宿主。Android 实际 minSdk 31、compile/target 37，Coil 3.6.3、CMP 1.12.1、Kotlin 2.4.20。
- `ReaderViewModel`/章节窗口产生稳定页 key/source → `ReaderScreen` 管理当前页/邻页、共享 ImageLoader → `ReaderContent.ReaderImageItem` 构建原有 Coil request → 平台解码 → `Image`/手势 graphicsLayer。网络仍由原图的 CopyManga client、会话 generation、风控和本地 fetcher 管理。
- Windows 宿主启用 `windowsReaderImageQuality`；`ReaderImageRequestLayout` 使用物理像素、半倍缩放档、120ms 防抖。`WindowsReaderImageDecoder` 使用 Skia MITCHELL 缩小、不放大源位图、输出上限 16,777,216 像素；内存键 `reader_sampling=windows-cubic-v1` 与 512px 邻页预加载区分。`lastSuccess` 在重解码或失败时保留可读图片。
- Android/iOS/Wasm 未启用 Windows 专用解码策略，仍走平台 Coil 路径。此分支不更改它们的采样质量或请求尺寸。
- ImageLoader 图级复用，普通原始编码数据在 Coil disk cache；下载文件与普通缓存分开。现有预加载最多 0–3 页、512px，释放时 dispose；章节窗口与图片/缩放状态已经有限保留。增强不扩大窗口，不取代原图缓存、导出或下载。
- **64MiB 是 Windows 输出位图上限，不代表原生解码峰值上限**；长图的完整源解码及其他平台峰值未测。此处只记录边界，不重新实现用户已完成的解码缩放工作。

## 能力评估与最小范围

| 路线 | 已核对的适配与成本 | 本阶段决策 |
| --- | --- | --- |
| 轻度自适应锐化 | 五点亮度高频校正；局部范围夹取、强边缘衰减、0.03 单通道最大偏移；不增加原图细节信息。Android AGSL 需要 API 33+ 硬件画布；桌面使用实际 Skia RuntimeEffect | 连接当前页、默认 0，限制额外离屏表面，保留立即关闭/原图回退 |
| waifu2x 2× | 官方 ncnn CLI 有 `-g -1` CPU、Vulkan GPU、tile、noise、TTA；不是 Android AAR/JNI 包。优先 `cunet`, `-n -1`, 2×，TTA 关闭 | 校验官方 Linux 包及模型，尝试真实推理；云端实际失败，应用后端保持不可用 |
| Anime4K | MIT；官方主线主要面向 1080p 动画；mpv GLSL 多阶段资源/纹理语义不能直接复制成单个 AGSL shader | 评估对照，不移植、不以自写锐化冒充 Anime4K |
| Real-ESRGAN 动漫 | BSD-3-Clause 代码；`RealESRGAN_x4plus_anime_6B` 原生 4×，`realesr-animevideov3` 是另一模型。PyTorch `outscale=2` 不等于网络原生 2×，必须区分导出图与运行时 | 评估对照，不把两个模型都打包；未执行 |
| ncnn | BSD-3-Clause，另有第三方 notices；支持 CPU/Vulkan 及多平台构建，但每个 ABI、驱动、线程与生命周期仍需适配 | native 必须留在平台适配层；不把 Android API 或 ncnn 放进 commonMain |

官方依据：[AGSL 平台范围](https://developer.android.com/develop/ui/views/graphics/agsl)、[RuntimeShader 使用](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)、[waifu2x 参数和构建](https://github.com/nihui/waifu2x-ncnn-vulkan)、[Anime4K 定位](https://github.com/bloc97/Anime4K)、[Real-ESRGAN 动漫模型](https://github.com/xinntao/Real-ESRGAN/blob/master/docs/anime_model.md)、[ncnn 构建](https://github.com/Tencent/ncnn/wiki/how-to-build)。上游性能宣传不能替代本机/平板性能测量。

## 代码、权重、发布包与校验

| 官方资源（检查日 2026-10-23） | 发布页显示的大小 | 许可/处理 |
| --- | --- | --- |
| waifu2x 20250915 Linux / Windows / macOS | 35 / 33.9 / 39.8 MB | 根 LICENSE 是 MIT；只下载官方 Linux 发布包作隔离实验，未安装到系统或应用 |
| 本次选择 `models-cunet/scale2.0x_model.bin` + `.param` | 2,776,400 + 7,249 bytes，约 2.65 MiB | 实际权重存在，逐文件 SHA-256 已记录；不需要打包全部约 35MiB 模型目录 |
| Anime4K v4.0.1 的 `Anime4K_v4.0.zip` | 758 KB | MIT；shader 包名称与 release tag 不同；未下载执行 |
| RealESRGAN_x4plus_anime_6B.pth (v0.2.2.4) | 17.1 MB | 代码 BSD-3-Clause；权重下载页未提供独立模型许可声明/可见 SHA-256，本阶段不分发、不加载 pickle 权重 |
| realesr-animevideov3.pth (v0.2.5.0) | 2.39 MB | 不与 anime_6B 混用；未下载 |
| ncnn 20261020 Android Vulkan shared / static zip | 29.2 / 38.8 MB | 多 ABI SDK 压缩包，不是单 ABI APK 增量，不能直接当作安装体积；未下载 |

来源：[waifu2x assets](https://github.com/nihui/waifu2x-ncnn-vulkan/releases/expanded_assets/20250915)、[Anime4K assets](https://github.com/bloc97/Anime4K/releases/expanded_assets/v4.0.1)、[anime_6B assets](https://github.com/xinntao/Real-ESRGAN/releases/expanded_assets/v0.2.2.4)、[animevideov3 assets](https://github.com/xinntao/Real-ESRGAN/releases/expanded_assets/v0.2.5.0)、[ncnn assets](https://github.com/Tencent/ncnn/releases/expanded_assets/20261020)。这些是发布页显示单位，实际已下载 Linux zip 为 36,658,685 bytes。

[waifu2x-ncnn LICENSE](https://github.com/nihui/waifu2x-ncnn-vulkan/blob/master/LICENSE) 和[原始 waifu2x LICENSE](https://github.com/nagadomi/waifu2x/blob/master/LICENSE) 均为 MIT。发布包有根 MIT 文本，但没有独立的逐模型 license/model-card 文件；模型目录归属及转换链需在正式分发清单中明确，不能把代码许可证检查写成所有训练素材/输出权利保证。[Anime4K LICENSE](https://github.com/bloc97/Anime4K/blob/master/LICENSE)、[Real-ESRGAN LICENSE](https://github.com/xinntao/Real-ESRGAN/blob/master/LICENSE)、[ncnn LICENSE](https://github.com/Tencent/ncnn/blob/master/LICENSE.txt) 应随正式采用的准确版本保留。未采用上游展示用漫画/动画图。

核验记录：

- 官方 Linux zip SHA-256（发布页提供，下载后匹配）：`848e0fba55657d34da90b775b8139e9806dc754798b029f95e106ba8850a731f`。
- 解压 executable SHA-256：`c18f4ec21879a8b91263551ebaab0a5b5f45ad4c456d90cf626aaaf561b42280`。
- cunet scale2 权重 SHA-256：`d82ed30207fd07e1c16726a9ca838ce44e6e25a105b0278c7ca681516ee0e18e`。
- cunet scale2 param SHA-256：`ccc3b2f1c9fad829ecdda40cac8dc853fcddf14917170efba888b256fbf9074a`。
- 对照上游 HEAD：Anime4K `7684e9586f8dcc738af08a1cdceb024cc184f426`；Real-ESRGAN `a4abfb2979a7bbff3f69f58f58ae324608821e27`；ncnn `cb65ca26ba955bf456f3a26e13e9a644769fc4a4`，其 20261020 release tag 为 `7e36d25cb153092ba87a60530455c7376fedf452`。
- 读取的 waifu2x 源码 master 是 `64914665c45893135c9e50c1c296170a121b9f77`；它与发布版本是两个身份，缓存不得只写 `latest`。

未来模型交付应使用固定 HTTPS URL、字节上限、审核后的模型清单与 SHA-256，流式写临时文件并校验后原子移动；中断/不匹配删除临时结果并保留旧版本。下载需明确用户选择，不得后台自装动态程序。此次工具不自动下载、安装或加载任意可执行文件；`evaluate.py --waifu-root` 只接受上面固定二进制与模型哈希。

## 阶段一：可运行轻量锐化

`LocalReaderSharpness` 默认 0，数值夹取 0…0.35，NaN/Inf 视为 0。可用测试宿主的 `CompositionLocalProvider(LocalReaderSharpness provides 0.20f)` 在会话中调节；不写入现有设置/备份格式，不新增通用设置 UI。桌面还可以给**应用 JVM**添加 `-Dcopymanga.reader.sharpness=0.20`，置 0 或删除该参数关闭。此参数不是 Gradle 的 `-P`，也不是只给 Gradle daemon 的 `-D`。Android 默认保持关闭，测试宿主通过 CompositionLocal 注入。

只在成功状态的当前页 Image 上附加效果，不影响占位、重试、菜单、评论、缓存和导出。分页效果在图片缩放层内，连续阅读在列表缩放层内，核函数单位是图片效果层像素；不是对源图做重采样。强边缘衰减、亮度局部范围夹取旨在限制 halo，不能保证所有图片都更好。

- Android：API <33、软件画布、编译效果失败均直接原图；原生 AGSL 实现在 androidMain。
- Desktop：Skia runtime shader，异常回退；原生 filter 随 composition 释放，关闭为无效果层。
- iOS/Wasm：明确 no-op，不宣称支持 shader。
- 宽高每边最多 4096 且面积最多 4,194,304 层像素，超限保持原图。RGBA8 离屏单表面约 16MiB 上限，但实际渲染器可能多缓冲/更高精度，不是应用内存保证。
- 全部平台仍保持既有 ImageRequest、decodeOptions、FilterQuality、512px 预加载、`windows-cubic-v1` 与 `lastSuccess` 逻辑。

## 阶段二：超分调度与结果缓存契约

`ReaderSrCoordinator` 是可测试的独立协程组件，暂未接到 ReaderScreen。默认 `UnavailableReaderSrBackend` 明确不可用，绝不会把插值/锐化标为超分。

```text
正常阅读原图（始终可用）
  └─ 未来调用 submit(current, [next, previous])
       ├─ 固定最多三个候选，StateFlow 合并快速翻页
       ├─ collectLatest 取消并等待上一轮结束，单个 inference
       ├─ 先当前页，后最多两张邻页；取消后拒收迟到结果
       └─ 独立结果缓存 → 匹配来源/版本的 Ready 才可叠加替换显示
             失败/超时/缺模型/超预算 → 原图
```

请求携带原始 bytes SHA-256、账号/请求模式/会话 scope、尺寸与 EXIF 方向；模型 ID、版本、权重+param digest、运行时/预处理版本、2×、noise、tile、TTA 和 sRGB/RGBA8 策略进入长度前缀编码的 SHA-256 键。URL 或 Coil 缩略图不够作为源身份。正式接线必须复用原图读取/风控路径、校验内容摘要，不能为了超分重新绕过图级网络客户端。

协调器的 LRU 最多三份编码结果且总预算16MiB；状态流只保留最多三个当前候选。被 Ready 状态引用的结果不因 LRU 淘汰立刻释放：最坏另有三份各16MiB，总活跃编码数据上界64MiB（通常共享引用更少），尚需与阅读位图/模型预算一起测量。未实现持久结果磁盘缓存、真实 tile sink、模型下载器或 JNI。缓存接口只适合当前原型，不应把示例 `workingBytes` 当实测激活峰值。未来磁盘结果应使用独立目录与配额，原子提交，不覆盖 Coil 原图和用户下载。

入场估算是输入 RGBA8 + 2× 输出 RGBA8 + backend.workingBytes，默认 96MiB，长边 >4096 直接回退。**2× 输出像素/位图大小为 4×，输入与输出同时存在至少为 5×**：1600×2400 输入约14.65MiB、输出58.59MiB，总73.24MiB，尚未计模型激活、编码缓冲和阅读器正在显示的图。1200×20000 长图仅这两份就约457.8MiB。因此未有 region decode/streaming tile output 之前不跑长图，不整章并行。

原生后端必须在 tile 边界响应取消；无法抢占的调用仍占用唯一槽，等它返回后才开始最新页。协调器须由同一串行 dispatcher（例如 Main）调用 submit/close，原生实现内部切换后台执行；不是可从任意线程同时调用的 API。30秒超时是协程逻辑超时，**不能杀死卡住的 JNI/GPU 调用**。进程隔离/驱动故障策略是后续 native 接入前置条件。关闭、后台、换账号都应 cancel/close；未接入前不宣称已验证这些产品生命周期。

## 阶段三：同批合成样张与实际阻碍

运行 `evaluate.py` 生成小字号中英文字、网点、细线/圆弧和彩色插画四类原创建图（384×256 reference → 192×128 source）。字体使用机器已有 Noto Sans CJK 并记录 SHA-256。未下载漫画内容。Bicubic 是离线对照，不改应用解码。Python 轻锐化是公式参考；实际桌面 shader 另由 Kotlin 测试对相同样张渲染。

```sh
python3 prototypes/reader-enhancement/evaluate.py --output /tmp/reader-samples
# 可选：必须先从官方 release 手动下载并验证压缩包 SHA-256。
python3 prototypes/reader-enhancement/evaluate.py --output /tmp/reader-samples \
  --waifu-root /path/to/verified/waifu2x-ncnn-vulkan-20250915-linux
```

本次实际尝试参数：`-g -1 -n -1 -s 2 -t 64 -j 1:1:1`，官方包加载成功且 help 可执行，但首次推理输出 `vkCreateInstance failed -9`，进程 SIGSEGV（-11），没有生成输出。源码 main 在选择 CPU 后仍有 GPU 实例初始化；没有调试栈，不能断言崩溃根因只在这一行。云端无 `/dev/dri`，没有 Vulkan 设备；没有 CMake/Android NDK，未另装这些工具或换未经核验的二进制。状态是**执行失败**，不是审批拒绝，也不是仍在运行。

因此 waifu2x 的样张质量、64/128 tile 接缝、峰值内存/延迟均未获得结果；没有假造“成功超分”图。工具会在首个后端失败后停止该后端并继续轻量评估。Anime4K 与 Real-ESRGAN 均未执行，风险只能作为待验证假设：线条粗细/文字结构变化、网点被去噪抹除或摩尔纹、halo、彩色边缘/ICC 偏移、分块接缝。Real-ESRGAN 尤其应检查生成纹理与汉字笔画变化；不能仅凭动画示例判断漫画适用性。

## 后续设备验收

1. Android API31/32 回退、API33+ 不同 Adreno/Mali GPU；Windows 集显/独显；iOS/Wasm 无效果原图。验证开/关、阅读模式、缩放、旋转、前后台与页面销毁。
2. 在相同原图/显示尺寸比较默认、0.20、0.35 锐化；原始像素可读性优先。小字逐字核对，网点密度与频谱、细线宽度、halo、彩色边缘/透明/ICC/HDR 单独记录。截图差异不等于可读性改善。
3. Android 原生桥接需固定 ncnn commit/ABI/NDK、CPU fallback、Vulkan capability probe、16KiB page-size/native packaging 检查、权重清单、JNI/NDK取消和资源释放。GPU 不可用、内存预算不足或模型不匹配必须返回原图。
4. cunet 2×、noise=-1/0、tile64/128/256，同一批图检查重叠/裁剪；上游 cunet scale2 padding=18，不能把它随意当通用模型 padding。对比单块参考与分块输出、边界误差，并计输入/输出/激活/编码缓冲的真实峰值。
5. 当前页优先/快速翻页/跳章/撤销/去重/后台/换账号；模拟损坏、超时、磁盘满、校验不符、进程/驱动失败；不得自动重试导致热量失控。
6. 目标平板冷/热态至少各10分钟，记录 p50/p95 首图与增强延迟、丢帧、RSS/GPU memory、温度、频率、功耗/电量、缓存命中与清理。云端编译/CPU 软件渲染不代表平板性能。

本轮实际验证结果、图像和日志保存在独立 `/workspace/enhancement-evidence`，不把生成图、大模型、二进制或逐轮日志提交到源码仓库。

## 复现编译与定向测试

```sh
# JDK21 + 仓库 Wrapper；单平台顺序执行，避免同时构建抢内存。
READER_ENHANCEMENT_SAMPLES=/tmp/reader-samples bash gradlew \
  :shared:desktopTest --tests '*ReaderSuperResolutionTest' --tests '*ReaderEnhancementTest' \
  --tests '*WindowsReaderImageQualityTest' --tests '*ReaderWheelZoomUiTest' \
  :desktopApp:compileKotlin -PenableAndroid=false --max-workers=1 --no-daemon
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  :shared:compileKotlinWasmJs --max-workers=1 --no-daemon
python3 -m unittest discover -s prototypes/reader-enhancement -p 'test_*.py' -v
```

`READER_ENHANCEMENT_SAMPLES` 指向先生成的四类样张目录；未设置时该导出辅助测试不执行图像输出，其他 shader/调度测试正常执行。共有18项新增 Kotlin 测试（10项调度协议 + 8项锐化），协议测试的后端只生成用于检查元数据的测试夹具，不能作为真实 SR 推理证据。Android app 单测不包含 shared commonTest，本原型 commonTest 由 desktopTest 执行。`compileKotlinWasmJs` 只证明编译，不代表浏览器运行或完整分发打包。iOS、AGSL 真机和 GPU 验证单独记录，不能由上述命令替代。
