# CMP 超分打包与模型下载

## 普通构建

Android arm64、Windows x64 的普通 Gradle / IDE 打包必须包含本平台运行时、manifest 和完整许可，但不包含模型。无需 readerSrBundle 额外参数。prepareReaderSrAndroid / prepareReaderSrWindows 是正式打包依赖；缺失或摘要不符会失败，不再静默交付无法超分的包。

固定运行库缓存：GRADLE_USER_HOME/caches/copymanga-reader-sr/v1/bundle。每次构建都重新核验。可由 native/reader-sr/build/bundle 或既有 build/imported/cmp-reader-sr-bundle 自动导入，reviewed bundle 的逐文件锁在 bundle-v1.sha256。readerSrBundle 保留为可选离线覆盖。

全新机器没有 bundle 时，prepare_bundle.py 调用既有 build.py 从固定官方源码构建。需事先准备 Python、git、CMake，以及 Android NDK（ANDROID_NDK_HOME）或 Windows llvm-mingw（LLVM_MINGW_ROOT），版本见 native/reader-sr/README.md。脚本不安装工具链，也没有私人 Library 或签名临时 URL。未发布公共 native bundle；无上述工具链的全新 CI 会明确失败。若要免准备工具链，需要所有者另行批准发布可验证固定二进制分发，不能把本机缓存当成已解决公共 CI 引导。

## 模型按需下载

模型来自 nihui/waifu2x-ncnn-vulkan 官方仓库固定提交 a86cfb043e6482b5f08778a114df9b5faf6e73b7 的 models/models-cunet，使用 raw.githubusercontent.com HTTPS，不跟随重定向。两个文件总计 2,783,649 字节（约2.65 MiB），大小及 SHA256 固定在 SrModelStore / ReaderSrModel。官方 MIT 与模型/引擎/平台许可继续随运行时包保留，不推断训练数据权利。

首次选择超分模式先显示下载大小并确认；未确认不发请求。全部下载并校验后，原子提交版本目录才启用。下载中/失败/取消/缺模型继续显示原图，临时目录会清理。相同模型路径共享协程锁与跨进程文件锁，避免重复下载；已有校验缓存可离线使用。支持删除、重下；删除先关闭超分，再等原生推理门空闲。Android 存在应用 filesDir，桌面存在应用自己的模型缓存，均独立于原图与下载文件。

## 设置接口与 UI 交接

- ReaderSrModels：state、downloadBytes、refresh、download、delete；状态为 Missing / Downloading(bytes,total) / Preparing / Ready / Cancelled / Failed(message)。
- ReaderSrSession.models 公开平台模型控制；模型未就绪时不会向推理协调器提交请求。
- ReaderSrModelControls：基础确认、下载进度、取消、删除重下接线；供 UI 任务串行统一审查，不作为最终视觉设计。
- ReaderSharpnessOptions：开关 / 强度滑杆 / 恢复默认；ReaderToolsSettings.sharpness 默认0，强度0…1，关闭后恢复上次非零强度（初次0.20）。既有数值原样保留，设置持久化并纳入备份。画质页显示当前页是否启用，提供“对比锐化前后”；对比只临时切换锐化，不修改保存的强度或 AI 设置，退出对比、换页、改变布局后恢复。
- 锐化保留亮度邻域限幅以防白边，不再因黑白反差大而关闭边缘处理。单个离屏表面上限为 8,388,608 像素、边长 4096，支持 2032×3048 平板及 3840×2160 单页；这不是长图分块方案，超过预算仍回退并提示。
- 设置入口：漫画阅读器 → 显示控制栏 → 设置 → 向下滚动至轻度锐化 / 超分辨率。Android 锐化要求API33+及硬件加速；桌面用Skia；不支持环境明确禁用。

原解码/缩放策略、原图字节/缓存/导出、原始页历史书签、RTL及双页行为保持；超分双叶仍显示原图，单页单任务推理。独立旧 Android 未增加超分，也未为它复制一套渲染与设置系统。

## 验证命令

```
gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :desktopApp:packageUberJarForCurrentOS :shared:desktopTest
python -B -m unittest discover -s native/reader-sr/tests -v
```

NativeReaderSrTest 显式下载并校验官方模型后做真实 CPU/GPU 测试；这是测试明确请求网络，不是应用自动下载。SrModelStoreTest 使用注入传输模拟失败/坏摘要/取消/并发/离线缓存，无模型时启动不联网。真实 Android 设备仍需另测服务IPC与GPU。

## 本轮最终范围与验证

页面布局保存在 ReaderPreferences.pageLayout，直接默认 SINGLE；不新增旧全局默认冻结、迁移标记或旧备份特殊逻辑。翻页动画仍为全局，配页仍按章节。ReaderTools.pageLayout 只作为传给既有阅读策略的当前漫画投影，布局 UI 写回 ReaderViewModel.setPageLayout，不写全局工具字段。重置显示设置明确保存当前漫画的单页快照。

模型下载的 startDownload()/cancelDownload() 由共享应用进程作用域托管；关闭设置/离开阅读器不取消已经确认的下载。下载完成只发布模型 Ready，不写 ReaderSrSession.settings；用户关闭增强后保持关闭。整个应用进程退出时不承诺断点续传；完整缓存可离线复用，未完成模型不启用。

模型删除先关闭本次增强，在后端推理门中等待现有任务退出；UI 不直接操作文件。模型下载与增强失败是独立类型。当前页增强不提供 retry API，因此没有假重试按钮；失败时保留原图。逐页锐化回报实际渲染表面预算/滤镜回退状态，不改变解码策略。

终验：两端 assembleDebug / 单测 / lint 成功，CMP Windows JAR 成功；CMP桌面672项（670通过、2平台跳过）、CMP Android57通过、独立Android180通过，native打包Python5通过。Windows CPU/GPU实际下载模型后均输出四张2倍PNG，GPU测试禁止CPU回退。普通APK/JAR包内只有运行时及许可，无模型。Windows无模型隔离启动窗口正常。

截图来自实际桌面Compose生产组件，状态交互测试使用注入模型状态，不能充当Android真机证据。Android仓库单测用原子DataStore接口夹具验证转换/新格式往返；其Android文件后端在Windows JVM上的rename行为不兼容，未修改生产后端。真实Android物理落盘、Service IPC、GPU/手势和长时间发热尚未实测。没有清空用户数据、推送、触发CI或发布。
