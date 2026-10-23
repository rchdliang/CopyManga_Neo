# waifu2x App 接入验证记录（2026-10-23，云端）

已从 unavailable 后端推进到真实原生推理：Linux CPU 经 Kotlin 应用适配器生成有效2×图片；Android arm64 APK、Linux/Windows x64便携JAR均已打包。默认关闭，原图优先，只有当前真实单页进入队列。GPU、Android服务IPC和Windows运行尚未实测。

基线与本地提交：

- 最新远端默认分支独立核对仍为 `CMP@098cb6b65005370edda90e86807712bfd85fc321`，没有借用UI任务的代码。
- 前一原型 `07b7db8ff16558d14ba48712e91e5508923789a9`；上游CPU修复 `3b82de7877bd840475d78fbab24b9bb7dfee781d`。
- 本轮 native worker/build：`3b68231249f703db6a658c73bd86217760d81713`。
- 本轮平台适配、阅读器接线和测试：`57e02ca7de1a651b2e548133f1f3890bbe136785`。
- 独立工作分支 `prototype/cmp-native-sr`；原checkout未改。无push、PR、merge、publish、Actions或用户本地环境修改。

## 验证证据分级

| 项目 | 结果 | 限制 |
| --- | --- | --- |
| Linux x64 native / Android arm64 JNI / Windows x64 native | 源码构建通过，固定模型摘要通过 | 后两者只编译；Windows为交叉构建 |
| Kotlin CPU集成 | 四类192×128输入→384×256有效PNG；像素与直接worker输出一致 | Linux云CPU，不是桌面GUI/真机 |
| desktop/shared定向测试 | 33通过，0失败/跳过 | 含真实CPU、实际Skia锐化及调度测试；不是全部共享测试全集 |
| Android应用单测 | 57通过，0失败/跳过 | JVM测试，不含服务IPC/设备测试 |
| Android Lint | 0错误，5原有警告 | AndroidPlatformHost的StaticFieldLeak×1、UseKtx×4 |
| Android debug APK | 打包通过，模型与安装.so摘要匹配 | 未安装，未启动服务 |
| Linux/Windows便携JAR | 打包通过，每包仅包含对应SR运行时，模型摘要匹配 | Linux适配器实测；Windows未执行；均未运行阅读器GUI |
| Wasm shared | 编译通过，明确原图回退 | 未浏览器运行；iOS未编译 |
| GPU | 无有效设备，probe返回CPU可用（-1），Vulkan初始化错误不崩溃 | 没有GPU真实推理证据 |
| 用户平板/手机及其他厂商硬件 | 未测 | 不宣称已达到目标设备性能 |

最终合并命令：`:shared:desktopTest`（NativeReaderSr、SrPngEncoder、ReaderSrSession、ReaderSuperResolution、ReaderEnhancement、ReaderImageQuality、ReaderWheelZoom、WindowsReader匹配测试）、`:desktopApp:packageUberJarForCurrentOS`、`:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:lintDebug`、`:shared:compileKotlinWasmJs`，带显式 `-PreaderSrBundle`。日志 `app-validation-final2.log`：BUILD SUCCESSFUL，108 tasks，1m45s。Windows额外带 `-Pcopymanga.desktopTarget=windows-x64`，`windows-jar.log`：BUILD SUCCESSFUL，22s。未触发Actions。

故障/恢复测试：模拟native子进程SIGSEGV后JVM继续；取消强制退出并reap；模拟GPU失败在自动模式只回退一次CPU，显式GPU/关闭CPU回退不偷换模式；源摘要变化、坏输出、模型篡改拒绝；关闭/后台/换页撤销任务；清理遗留作业但保留另一实例锁定目录；PNG跨行/跨块RGB与透明度完全往返，超限编码停止。GPU模拟只是错误路径单测。

开发中遇到并修复：AGP9.4生成assets必须使用variant source API；Android JNI异常选项；JUnit协程测试末尾Boolean导致非void初始化错误。首次worker资源测量发现环境没有`/usr/bin/time`，改用Python `wait4` 获取该子进程独立rusage，测量成功。早期失败日志保留；没有把失败测试删去。标准 `git diff --check` 会把内嵌上游补丁的空白上下文行识别为尾空格，因此仅对该补丁文件排除检查，其他源码检查通过。

## 同批样张：有效输出与风险

| 样张 | 应用适配器耗时/秒 | worker峰值RSS/MiB | Bicubic MAE | waifu2x MAE |
| --- | ---: | ---: | ---: | ---: |
| 小字 | 1.143 | 56.24 | 11.336 | 5.106 |
| 网点 | 0.881 | 56.05 | 21.325 | 12.731 |
| 细线 | 0.818 | 55.82 | 18.659 | 6.544 |
| 彩页 | 0.792 | 55.70 | 2.050 | 0.731 |

两个独立测量路径：应用适配器时间包含资源准备/自检（首次）、解码、worker与编码；RSS来自另一次原生worker测量，不含JVM/阅读器。worker自身耗时1.21–1.93秒，期间Gradle并行运行，不能据此横向比较CPU、GPU或手机。

MAE为对合成高分辨率参考的0–255 RGB平均绝对误差，不是OCR准确率。检查 `native-app-comparison.png`：文字较Bicubic清晰，但最小字笔画有粘连/变形；高密网点仍有合并与强度改变；细线交汇区域有重构/断续风险。彩页轮廓更清楚，但这不是真实漫画整体品质结论。原先tile32与单块结果存在明显差异（小字最大通道差99/255），此次固定小tile取舍内存，不能宣称无接缝或无halo。原先Skia轻锐化对照另保留，Anime4K/Real-ESRGAN未执行，无同图排名。

## 产物与实际修改

| 本地产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| app-debug.apk | 89,731,059 | `9a490bfb8210d770f7a5eaa0b157828878580bf8cd16d5bfc2d2ef9296b3d0a0` |
| CopyMangaNeo-linux-x64-0.10.23.jar | 100,271,016 | `c713fba266e1c03789f0cdc7a6f5c8ef5d7444b17ef82f45823ab56fe14ac124` |
| CopyMangaNeo-windows-x64-0.10.23.jar | 98,598,348 | `8b717d1b8482796f4dbea023d5cc0c66aaffac74118efd410e4ed72460af0480` |

APK位于`app/build/outputs/apk/debug/`；JAR位于`desktopApp/build/compose/jars/`，需要JDK21。这些是云端构建路径，不是用户可下载附件；当前五文件报告包不含APK/JAR、模型或第三方程序。Linux worker要求GLIBC_2.38、GLIBCXX_3.4.32，尚未面向旧发行版重编；Windows依赖系统UCRT/KERNEL32，未运行验证。Android JNI为16KiB ELF LOAD对齐，合并Manifest的服务确认为non-exported、`:reader_sr`、stopWithTask。

`native/reader-sr`新增引擎、CLI/JNI、固定源码构建脚本、CPU补丁和说明；`shared/src/jvmMain/.../sr`新增通用平台后端/PNG编码；Android/Desktop各新增codec、worker、platform factory；common新增会话、设置与降级；Web/iOS新增不可用工厂；新增同批合成fixture及测试。构建配置增加显式bundle打包、JNI保留规则、独立服务和进程初始化分支。完整文件列表在源码补丁及`changed-files.txt`。

已有阅读器只改`ReaderScreen.kt`、`ReaderContent.kt`的最小连接，未修改`ReaderImageQuality.kt`、`ReaderWheelZoom.kt`、`WindowsReaderImageDecoder.kt`（对07b7逐文件diff为空）。没有重做既有解码/缩放。两处接线文件及Gradle/Manifest/Application可能与UI和双页任务冲突，需逐块合并；本任务不自动覆盖另一任务。

## 剩余条件

需要Android真机验证独立服务崩溃/退出/重建、JNI装载、GPU自检/实际出图、CPU回退和长时间阅读；需要Windows x64验证启动、Unicode路径、驱动失败与进程回收。目标为骁龙8Gen2平板、暂按8Elite理解的“8E”手机，以及代表性MediaTek、AMD/Intel和NVIDIA设备。尚无确切机型/驱动/RAM，不能按芯片品牌承诺支持。记录峰值PSS/RSS/GPU内存、温度、功耗、延迟分位、翻页流畅度与文字/网点损失。

超限长图目前明确原图回退，尚无region decoder/流式输出sink；Android x86、Windows ARM64、Linux ARM及Apple未交付已验证native包；Web保留回退。无NPU实现。以上为未测/有意限制，非权限拒绝或假成功。
