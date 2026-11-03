# 构建与版本配置

项目结构与平台能力见 [架构说明](ARCHITECTURE.md)。以下命令从仓库根目录执行；Windows 使用 `.\gradlew.bat`，macOS/Linux 使用 `./gradlew`。

## 环境准备

- 使用完整 JDK 21 和仓库自带的 Gradle Wrapper。Gradle 版本以 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 为准；依赖版本以 [版本目录](../gradle/libs.versions.toml) 和各模块构建脚本为准。
- Android SDK、Build Tools、NDK、CMake 与应用依赖版本统一在 [版本目录](../gradle/libs.versions.toml) 中维护，app/shared 引用同一配置。AndroidX 与 Compose Multiplatform 使用各自的库坐标及版本，不强行合并两套 Material3/Navigation。Foojay 是 settings 阶段插件，其版本在 settings.gradle.kts 中声明。
- 在本地 `local.properties` 设置 `sdk.dir`，或设置 `ANDROID_HOME`。CMP CI 通过 `python3 .github/scripts/android-sdk-packages.py` 读取同一版本目录，安装 SDK 平台、Build Tools、CMake 和 NDK；该脚本需要 Python 3.11+。
- 桌面安装包使用对应操作系统及架构的 JDK；Windows EXE 安装向导需要 Inno Setup 6.5+，Linux DEB/RPM 需要系统打包工具。Windows 编译器可放入 PATH，也可通过环境变量 `ISCC` 指定 `ISCC.exe` 完整路径；标准的 Inno Setup 6 安装目录会自动检测。
- CMP Android、Windows、Linux 的 Vulkan 画质修复需要 Python 3、CMake 和宿主 C++ 编译器；Windows 使用 Visual Studio C++ Build Tools，Linux 使用 GCC/Clang。Android NDK 版本来自版本目录，CMake 版本以应用构建脚本为准，可由已接受许可的 SDK 管理器安装。模型随包分发，首次构建需下载并编译固定版本的 glslang。Windows Native Image 自身的工具链要求仍按下文执行。
- iOS 构建需要 macOS 和 Xcode；直接安装需要签名。未签名设备 IPA 可交给 SideStore / AltStore 使用用户自己的账号重签名。
- 浏览器打包需要 Node.js、Yarn Classic 和生产优化所用的 Binaryen `wasm-opt`。可参考 [手动 CI](../.github/workflows/multiplatform-manual.yml) 的工具安装步骤。
- 内存与并发默认值见 [gradle.properties](../gradle.properties)。内存受限时使用 `--max-workers=1`，不要同时运行多个平台构建。

Android Studio 打开仓库根目录，完成 Gradle Sync 后选择 `app` 运行配置。修改构建脚本后重新同步；不要在仍运行的构建中改动输入文件。

画质修复构建由 `gradle/reader-restore.gradle.kts` 管理：`prepareReaderRestoreModels` 将十组模型编译到 `build/reader-restore/generated-resources`，Android assets 与桌面资源自动依赖该任务；`prepareReaderRestoreDesktop` 编译并打包宿主 JNI 库。Android 原生库由 externalNativeBuild 构建。普通构建不需要手工复制模型或运行库。桌面跨平台打包需用 `-PreaderRestoreNativeLibrary=<目标平台库路径>` 提供与 `copymanga.desktopTarget` 匹配的预编译库，不会自动跨平台编译。运行时需要可用的 Vulkan GPU 驱动；缺少驱动时应用保留原图。

画质修复构建使用的 glslang 仅为宿主着色器编译工具，不链接或分发进应用；其源码下载中保留上游 `LICENSE.txt`。该工具包含多项许可证，不能概括为单一 MIT；若另行分发构建工具包，应保留该工具及其依赖的完整声明。应用随包分发的 Anime4K 模型和 Vulkan-Headers 声明由模型资源任务自动复制，版权信息不会因生成 SPIR-V 而省略。

## 修改版本号

发布版本和平台升级编号只编辑根目录的 [version.properties](../version.properties)；应用内显示的 Git 提交数在构建时生成，不写入此文件：

| 字段 | 含义 |
| --- | --- |
| `COPYMANGA_VERSION_BASE` | 三段基础版本，如 `0.10.19`；用于 iOS 短版本 |
| `COPYMANGA_VERSION_HOTFIX` | 可选修订号；填 `1` 时发布版本为 `0.10.19.1`，留空时使用基础版本 |
| `COPYMANGA_VERSION_CODE` | 递增的整数构建号，用于 Android、iOS 和 Windows 安装包的版本管理 |

这个文件的赋值语法同时适用于 Java Properties 和 Xcode 配置，不要在其中放本地路径、密钥或机器专用设置。

- 根 Gradle 脚本在构建时执行 `git rev-list --count HEAD`，各平台关于页显示“发布版本+提交数”，例如 `0.10.30+181`。同时通过 `git rev-parse --verify HEAD` 读取完整提交哈希，固定取前七位，生成 Android 系统包管理器使用的 `versionName`，例如 `0.10.30-d3b3d30`。同一次提交重复构建或仅修改未提交文件时两个后缀均不变；主版本仍只在 `version.properties` 中维护。
- 构建需要 Git 和完整仓库历史；浅克隆先执行 `git fetch --unshallow`，CI checkout 使用 `fetch-depth: 0`，避免生成错误的提交数。配置缓存会跟踪 Git 输出，新增提交后自动更新生成版本。
- 发布标签、桌面打包元数据及 iOS 系统版本沿用数字版本，避免平台版本格式限制；`COPYMANGA_VERSION_CODE` 仍独立递增。Android 附件名使用“版本-七位哈希+提交数”，Windows 附件名使用“版本+提交数”，不把平台升级编号当作 Git 提交数。
- Windows Inno Setup 安装向导显示数字发布版本（含可选修订号，不含 `+提交数`）；Compose 内部 Windows 应用镜像仍使用“基础版本前两段 + 构建号”的三段版本。iOS 系统版本使用三段基础版本和构建号，各平台应用内显示带 `+提交数` 的版本；不需要另改平台版本常量。
- `:shared:generateReleaseVersion` 自动生成共享 Kotlin 常量，所有 Kotlin 目标的编译任务自动依赖它。生成文件位于 `shared/build/generated/releaseVersion/commonMain`，不要手工编辑。
- iOS 的 [Config.xcconfig](../iosApp/Config.xcconfig) 直接包含同一文件，将其映射到 `MARKETING_VERSION` 与 `CURRENT_PROJECT_VERSION`；Debug/Release 共用配置。
- Android Debug 与 Release 使用相同版本名。
- Android 宿主向关于页和更新检查传入共享显示版本，`BuildConfig.VERSION_NAME` 保留系统安装包版本。更新比较忽略提交数和七位哈希，不把哈希后缀当作预发布；同一数字发布版本的不同提交不触发版本升级提示。
- 更新元数据同时读取 Release 的附件列表，仅接受本仓库、对应标签的 HTTPS 下载地址。Android 按 `SUPPORTED_ABIS` 顺序匹配当前版本的 APK，再尝试通用包；Windows 将系统架构映射为 x64/arm64，分别提供已有的 ZIP、EXE 附件。未知架构、无匹配附件或不支持直接包下载的平台使用发布页入口，不跨架构选择安装包。
- 版本回归测试读取 Gradle 传入的期望值，升级时不需要另改测试常量。

可以单独检查版本生成及桌面默认元数据：

```sh
./gradlew :shared:generateReleaseVersion :app:generateDebugBuildConfig
./gradlew :shared:desktopTest --tests '*DesktopBuildMetadataTest' -PenableAndroid=false
```

## Android

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，Debug 与 Release 包名均为 `com.rchdliang.copymanga_neo`，桌面名称为“CopyManga_Neo”。原 Android 分支改用 `com.rchdliang.copymanga_neo.Android` 与“CopyManga_Neo-Android”，两个分支可以并存，私有数据分别保存。

App 请求模式所需的 `COPYMANGA_TAP_STRING` 从构建进程环境变量或本地 `local.properties` 读取，环境变量优先。没有该配置仍能构建，但 App 模式不可用。新 worktree 不会自动复制被忽略的本地配置，需要单独配置。不要把实际值写入源码、示例或日志。

Release 构建：

```sh
./gradlew :app:assembleRelease
```

需要本地 `keystore.properties` 的 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 及对应密钥库。普通本地 Debug 使用调试签名；`-PuseReleaseSigningForTest=true` 让 CMP Debug 包使用已有发布签名，签名材料不完整时构建失败。

CMP Debug 与 Release 使用相同安装标识；签名相同且版本满足要求时会覆盖安装并使用同一份数据。切换调试签名与发布签名前先备份个人数据。设备测试使用 `:app:connectedDebugAndroidTest`，需先连接目标设备；构建成功不等于设备操作验证完成。

## 桌面

Windows x64 默认使用 Nucleus Tao 窗口，日常验证直接在 JVM 上运行，发布时再编译 GraalVM Native Image。两者使用同一个 `NativeMainKt` 入口、窗口/托盘集成和共享业务代码；文件夹名 `src/native` 不表示只能生成原生 EXE。

在 IntelliJ IDEA / Android Studio 同步项目后，选择 **Windows Desktop**，点击 ▶ Run，执行 `:desktopApp:run`。它只编译变化的代码和准备资源，不生成 Native Image 或安装包。需要验证原生版时选择 **Windows Desktop Native**，执行 `:desktopApp:runGraalvmNativeDistributable`。

| 目的 | 任务 |
| --- | --- |
| JVM 快速运行当前 Windows 界面 | `:desktopApp:run` |
| 仅编译/组装代码，或附带本模块检查 | `:desktopApp:assemble` / `:desktopApp:build` |
| JVM 热重载 | `:desktopApp:hotRun` |
| 包含 Java 运行时的 JVM 应用目录 | `:desktopApp:createDistributable` |
| 运行 JVM 应用目录 | `:desktopApp:runDistributable` |
| JVM 便携 EXE | `:desktopApp:packagePortable` |
| 原生应用目录 / 运行原生目录 | `:desktopApp:createGraalvmNativeDistributable` / `:desktopApp:runGraalvmNativeDistributable` |
| 原生便携 EXE | `:desktopApp:packageGraalvmPortable` |
| 原生安装向导 | `:desktopApp:packageInnoSetup` |
| 原生免安装 ZIP | `:desktopApp:packageWindowsPortableZip` |

`runRelease`、`createReleaseDistributable`、`packageReleasePortable` 执行插件的 JVM Release 流程；`createRuntimeImage` 为 JVM 分发包准备精简运行时。`desktopApp:test` 只运行该模块自己的测试；共享业务测试需执行 `:shared:desktopTest`。

热重载需要 JetBrains Runtime（JBR），普通 JVM 运行只需要 JDK。settings 中的 Foojay 工具链解析器允许 Gradle 按需下载 JBR，首次热重载可能需要联网下载；不影响普通 `run`。热重载不能替代原生版的反射、JNI 和打包验收。

Gradle / Kotlin 编译仍需要 JDK 21；Native Image 使用版本目录固定的 GraalVM Community，由 Nucleus 自动下载，也可用 `GRAALVM_HOME` 指向匹配工具链。Windows 还需要 Visual Studio C++ Build Tools 与 Windows SDK。建议为原生编译预留 8 GiB 堆（`-PnativeBuildHeap=8g`），并限制 Gradle 堆和构建并发。运行交付的 EXE 无需安装 Java，但必须保留同目录的 DLL、bin 和 lib 文件。

```sh
./gradlew :shared:desktopTest :desktopApp:compileKotlin -PenableAndroid=false
./gradlew :desktopApp:run -PenableAndroid=false
```

`-PenableAndroid=false` 只排除 Android 应用模块 `:app`，适用于桌面、浏览器及独立 iOS 构建；`shared` 仍声明 Android 目标。该参数仅接受 `true` / `false`，默认 `true`。Linux 使用 `:desktopApp:packageDeb`、`:desktopApp:packageRpm`，输出位于 `desktopApp/build/compose/binaries`。

Windows x64 原生安装包在 Windows x64 上构建：

```powershell
.\gradlew.bat :desktopApp:packageInnoSetup :desktopApp:packageWindowsPortableZip -PenableAndroid=false --no-daemon --no-configuration-cache --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8'
```

两个任务先共用 `createGraalvmNativeDistributable` 生成的 `desktopApp/build/compose/binaries/main/graalvm-app/CopyMangaNeo` 原生应用目录。安装程序通过 [Inno Setup 脚本](../desktopApp/installer/CopyMangaNeo.iss) 编译；[ZIP 打包脚本](../.github/scripts/build-windows-portable.ps1) 保留完整目录（包括隐藏文件、DLL、bin、lib 和资源），并写入 Nucleus 原生启动标记。输出位于 `desktopApp/build/installer`，分别为 `CopyMangaNeo_windows_<版本+提交数>_x64_setup.exe` 和 `CopyMangaNeo_windows_<版本+提交数>_x64_portable.zip`，各有 SHA-256 文件。CI 同时构建并上传两种包，无需重复编译原生程序。

免安装 ZIP 解压到独立目录后运行 `CopyMangaNeo.exe`，无需安装 Java；不要只复制 EXE。这里的“便携”指免安装，用户资料仍位于 `%LOCALAPPDATA%\CopyMangaNeo`，与安装版共用 DPAPI 凭据和实例锁，不会自动在解压目录保存数据。测试独立资料时传入 `-Dcopymanga.dataDir=<目录>`；ZIP 的打包测试不替代真实机器解压启动验收。

Linux 和 Windows ARM64 使用 JVM 构建。Windows x64 默认通过 Nucleus 窗口快速运行；`-PdesktopRuntime=jvm` 会选择 `desktopApp/build-jvm.gradle.kts` 和 `MainKt` 窗口入口，用于跨平台及便携 JAR 构建。切换构建脚本后应重新同步 IDE。

JVM 应用目录位于 `compose/binaries/main/app`，原生应用目录位于 `compose/binaries/main/graalvm-app`。重新打包前退出从相应构建目录启动的应用，否则 Windows 可能锁住 EXE、DLL 或 `icudtl.dat`，导致复制失败。日常使用 JVM `run` 可避免占用原生分发目录。两种运行方式默认共用用户资料和实例锁；已有实例运行时，新启动会请求打开已有窗口。

安装向导提供简体中文／英文，允许选择目录，默认安装到当前用户的 `%LOCALAPPDATA%\Programs\CopyMangaNeo`，创建开始菜单入口；桌面快捷方式可选，完成页默认勾选「运行 CopyManga Neo」，静默安装不启动应用。安装程序使用 `desktopApp/icons/copymanga-neo.ico`，快捷方式与卸载入口使用应用 EXE 图标。简体中文资源固定来自 [Inno Setup is-6_7_1](https://github.com/jrsoftware/issrc/blob/is-6_7_1/Files/Languages/Unofficial/ChineseSimplified.isl)，保留译者声明。

原生 Windows 通知使用与开始菜单快捷方式一致的 AUMID；安装器写入 Nucleus 的安装类型标记。直接运行构建目录用于开发验证时，系统通知可能需要先完成一次安装，托盘入口和任务栏进度不依赖通知弹出。升级到原生版时，仅在检测到旧 jpackage 配置后清理安装目录中的旧 `app` / `runtime`，不处理独立的用户资料目录。

首次从旧 MSI / jpackage EXE 切换时，先备份个人数据，并从 Windows 设置卸载旧程序，再安装新包；两种安装器的卸载记录不同，不能视为自动迁移。后续 Inno Setup 版本使用固定 AppId，直接覆盖安装。卸载脚本只移除安装文件，不主动删除应用数据。安装向导、快捷方式、完成页启动、卸载与跨版本覆盖安装仍需在 Windows 实机验收，编译成功不能替代这些检查。

仅制作需要外部 JDK 的便携 JAR 时，可以选择 Windows 原生库：

```sh
./gradlew :desktopApp:packageUberJarForCurrentOS -PdesktopRuntime=jvm -Pcopymanga.desktopTarget=windows-x64 -PenableAndroid=false
```

ARM64 改用 `windows-arm64`。输出位于 `desktopApp/build/compose/jars`，文件名包含配置版本；使用匹配架构的 JDK 21 运行 `java -jar`。跨平台选择仅用于打包，不能在 Linux 执行 Windows 原生代码或生成 Windows 安装程序。

打包前置任务会检查重复类、服务描述符、签名块和多版本 JAR 语义；不要绕过检查来强行合并依赖。诊断文件位于 `desktopApp/build/reports/portable-jar`。

桌面默认关闭开发专用行为。需要调试界面时，把 `-Dcopymanga.debug=true` 传给应用进程，而不是仅传给 Gradle。Native Image EXE 和 JVM 都接受 `-Dcopymanga.dataDir=...`；不要让多个运行时同时写入同一目录。图片转换与普通剪贴板仍使用兼容的 AWT/ImageIO 适配层；Tao 窗口、敏感剪贴板、文件对话框、托盘、通知和任务栏使用原生接口。Native Image 所需的项目 JNA 代理登记位于 `desktopApp/src/main/resources/META-INF/native-image`。

## iOS

1. 在 Apple Silicon Mac 上打开 `iosApp/iosApp.xcodeproj`。
2. 选择 `CopyMangaNeo` scheme 和 ARM64 模拟器或实体设备。
3. 实体设备选择自己的签名团队，然后 Build/Run。项目构建阶段会调用 `:shared:embedAndSignAppleFrameworkForXcode`。
4. 不构建 Android 应用时，给 Xcode 启动环境设置 `ORG_GRADLE_PROJECT_enableAndroid=false`。

App 模式配置可通过复制 `iosApp/LocalSecrets.xcconfig.example` 为被 Git 忽略的 `iosApp/LocalSecrets.xcconfig` 提供。Debug 也支持进程环境变量 `COPYMANGA_TAP_STRING`。通过构建设置写入 Info.plist 的值可从应用包中读取，不应存放个人密码或账号 token。

`:shared:compileKotlinIosArm64` 只验证共享 KLIB；Swift 编译、框架链接、签名、系统选择器和设备运行需要 Xcode 环境验证。

发行包构建只在真实 Xcode 脚本阶段调用一次 `:shared:embedAndSignAppleFrameworkForXcode`，完成 ARM64 Release 框架链接、资源同步及装配，再进行 Swift 编译。开启 Gradle 构建缓存和任务耗时报告，保留 Release 优化；不再在 Xcode 外预先执行独立链接任务，避免两套任务环境造成重复链接，也不通过跳过钩子省略资源同步。

CI 使用 `build-ios-ipa.sh` 完成一次构建，旧 `framework`、`package` 分阶段参数不再接受。按操作系统、架构、Xcode 构建版本及依赖配置缓存 `~/.konan`，打包成功后保存缓存。通过 `COPYMANGA_IOS_BUILD_LOG` 将钩子内的 Gradle 输出写入 `build/ios-build-logs/kotlin-framework.log`，外层计时器实时转发，避免等待 Xcode 脚本阶段结束才显示日志。每个阶段每分钟输出存活提示，提示仅表示进程尚未退出，不表示编译百分比。各阶段耗时和退出码保存在 `build/ios-build-timings/`；其中 Xcode 总耗时包含内部 Kotlin 框架耗时，两者不能相加。Gradle 任务耗时报告位于 `build/reports/profile/`；CI 无论成功失败均尝试随 `ios-build-result` 上传日志与报告。首次缓存未命中仍需下载和初始化，实际加速幅度须比较后续同环境构建的报告。日常 Xcode/IDE 构建继续使用原框架任务及 IDE 已完成构建时的跳过约定，不要求设置 CI 日志路径。

未签名真机发行包（JDK 21、macOS/Xcode，在仓库根运行）：

```sh
bash .github/scripts/build-ios-ipa.sh
```

脚本构建 Release / iphoneos / arm64，验证 Mach-O 平台、Bundle ID、版本和随包资源，再生成 `build/CopyMangaNeo_ios_<版本+提交数>_arm64_unsigned.ipa`、SHA-256 与提交/版本清单。不会上传、签名或发布。CI 的 iOS 选项执行相同脚本，Artifact 可下载后交给 SideStore / AltStore Classic 重签名安装；模拟器包不能改名充当 IPA。

首次发布前在真实 iPhone 验证安装、登录、阅读、离线重开，以及两个版本覆盖安装后历史/书签/小说标注/下载保持。刷新签名与更换 Apple Account 分别验证；不要卸载原应用来更新。当前下载引擎要求保持前台。

通过真机验收后，将已验收的同一 IPA 与校验文件附到对应提交的 GitHub Release；在此之前不自动发布未经验证的设备产物。免费侧载的刷新周期与数量限制以 SideStore / AltStore 当前说明为准。

## 浏览器

```sh
./gradlew :shared:wasmJsBrowserDevelopmentRun -PenableAndroid=false
./gradlew :shared:wasmJsBrowserDistribution -PenableAndroid=false
```

生产输出位于 `shared/build/dist/wasmJs/productionExecutable`。生产站点使用 HTTPS；本地开发可使用 localhost。开发和生产 webpack 任务请分开调用，避免共用 npm 输出产生冲突。

漫画图片 CDN 未提供跨域许可，纯静态服务器无法供 Compose 画布读取这些图片。本地验证封面和详情页时，使用仓库提供的同源图片转发宿主：

```sh
./gradlew :shared:wasmJsBrowserDevelopmentExecutableDistribution -PenableAndroid=false
node .github/scripts/serve-wasm.mjs shared/build/dist/wasmJs/developmentExecutable 8081
```

打开 `http://localhost:8081/`。该服务仅监听本机，启动时给页面启用 `copyMangaNeoConfig.imageProxy`；只转发指定漫画 CDN 的 HTTPS 公开图片，验证公网地址、图片签名和大小，不携带账号凭据、不跟随重定向。停止服务使用 Ctrl+C。也可将目录参数换成生产输出目录。

普通静态托管不会自动开启图片转发；发布到其他宿主时需要部署同源 `/__copymanga_image?url=...` 图片服务并显式启用配置。不要把本地脚本直接开放到公网；公网部署需要独立的访问控制和资源限额。API、登录和章节目录仍由浏览器直连，仍受上游跨域策略约束。

构建使用 PATH 中的 Node、Yarn 和 Binaryen；也可通过 `-Pcopymanga.nodeCommand=...`、`-Pcopymanga.yarnCommand=...`、`-Pcopymanga.binaryenCommand=...` 指定。保持自动工具下载关闭，以遵守设置中的仓库策略。

宿主可在应用脚本之前设置 `globalThis.copyMangaNeoConfig.tapString`。本地 `shared/src/wasmJsMain/resources/copy-manga-config.local.js` 被忽略，但需要由本地页面显式加载。浏览器配置对访问者可见，不应包含账号凭据；配置不会解除 CORS、禁用请求头或代理限制。

`gradle.properties` 中的 Wasm 增量链接配置及堆大小用于当前工具链。遇到旧链接缓存错误时，先停止构建，再只清理相关的生成链接缓存；不要删除源文件、账号数据或其他平台产物。

## CMP 自动构建及三端发布草稿

[CMP builds and release draft](../.github/workflows/cmp-release.yml) 在每次推送到 `CMP` 分支时自动构建 Android 签名 Release APK、Windows x64 Native Image 安装程序和免安装 ZIP，产物可从该次 Actions 的 Artifacts 下载，保留七天。iOS ARM64 未签名 IPA 由手动多平台工作流选择 iOS 生成。一次推送包含多个提交时构建分支的最新提交；不同提交的推送不会互相取消。

自动构建只监听 `push.branches: [CMP]`，不监听标签或 PR：同时推送 CMP 分支和 `v*` 标签只启动一次自动构建；之后给同一提交补推标签也不会再次构建。标签本身不会创建 Release。普通推送不校验已有发布目标，也不创建或更新发布草稿，因此同一主版本可以持续提交构建。

手动发布只允许选择 `CMP` 分支，复用运行开始时同一提交已成功的 Android/Windows 自动构建和 iOS 手动构建产物，不再编译或打包。本流程不包含 Linux、Wasm；原 Android 自动工作流和多平台手动测试工作流保持独立。

如已创建 `v<数字发布版本>` 标签，手动运行时可填写 `release_tag`。此时发布工具仍取自选定的 CMP 提交，但版本配置、提交数和三端产物均固定取自该标签对应提交，并验证标签名称与其版本配置一致；不会移动标签或改用较新的应用产物。留空时沿用运行开始时的 CMP 提交。此功能用于在更新发布工具后继续发布已经完成构建的标签版本。

1. 在 `version.properties` 确定版本名和递增构建号，将准备发布的修改提交并推送到 CMP；等待 Android/Windows 自动构建成功，并在手动多平台工作流选择同一 CMP 提交构建 iOS。然后可将 `v<数字发布版本>` 标签推送到该提交，在 Actions → CMP builds and release draft → Run workflow 选择 CMP。工作流文件须已存在于默认分支才能显示手动入口。
2. 工作流固定运行开始时的提交，选择本仓库最新可用的成功 Android/Windows `push` 运行和 iOS `workflow_dispatch` 运行。三个平台任务必须实际成功，Artifact 完整且未过期；Android/Windows 必须来自同一次自动构建，iOS 来自同一提交的多平台工作流当前重跑次数。找不到时直接失败，提示等待或重跑对应来源构建，不会回退到其他提交或自动重新构建。
3. 按固定的来源运行 ID 和 Artifact ID 下载原文件，摘要不匹配时失败；上传前再次核对来源运行、重跑次数和附件 ID。复用四个 Android Release APK、Windows x64 EXE/ZIP 和 iOS ARM64 IPA；核对 iOS 清单的提交、版本、构建号、架构、未签名状态及 SHA-256 后，创建 `v<数字发布版本>` 的 **GitHub Release 草稿**。来源构建已执行各平台配置及打包检查；手动发布保持安装包字节不变，生成汇总清单。草稿不设为 Latest，也不会自动公开发布。
4. 从草稿下载最终安装包，验证三端安装、启动、阅读、下载及升级后的数据保留；iOS 需自行侧载签名。补充更新说明后，维护者手动发布。工作流只做构建及产物检查，不替代实机验收。

附件命名示例（发布版本 `1.2.3`、Git 提交数 `234`、短哈希 `1a2b3c4`）：

```text
CopyMangaNeo_android_1.2.3-1a2b3c4+234_arm64-v8a.apk
CopyMangaNeo_android_1.2.3-1a2b3c4+234_armeabi-v7a.apk
CopyMangaNeo_android_1.2.3-1a2b3c4+234_x86_64.apk
CopyMangaNeo_android_1.2.3-1a2b3c4+234_universal.apk
CopyMangaNeo_windows_1.2.3+234_x64_setup.exe
CopyMangaNeo_windows_1.2.3+234_x64_portable.zip
CopyMangaNeo_ios_1.2.3+234_arm64_unsigned.ipa
```

Release 上传七个安装产物、`ios-device-build.json` 和 `release-manifest.json`，共 9 个附件，不上传单独的 `.sha256` 文件，附件摘要由 GitHub 提供。来源 Artifact 中的校验文件仍用于发布前核对。清单区分数字发布版本、平台升级编号、Git 提交数和完整提交哈希，并记录两次来源构建的运行 ID、重跑次数、链接和三份 Artifact ID；产物缺失、校验不符或属于其他提交时拒绝发布。

自动构建使用仓库 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD` 和 `COPYMANGA_TAP_STRING`。前四项必须对应发布密钥，以保持覆盖升级能力。`prepare-test-signing.py` 负责恢复签名材料，自动构建执行 `:app:assembleRelease`。手动发布不读取这些 Secrets，只使用 `GITHUB_TOKEN` 的 Actions 读取权限下载产物及 Contents 写入权限维护草稿。本地 `keystore.properties` 与密钥库不会自动进入 Actions，不能提交或上传为产物。

若本地签名文件位于另一个 checkout，本地打包时需单独准备 CMP 的被忽略配置，并按原文件所在目录解析相对 `storeFile`；不能直接假设相对路径在 CMP 目录仍有效。这与 Actions 使用仓库 Secrets 是两条独立路径。

工作流不改写 `version.properties`；应用版本的 `+提交数` 由构建脚本自动生成。手动发布阶段不移动现有标签、不覆盖已公开 Release：相同数字发布版本的已有标签必须指向同一提交，已有草稿也必须固定到同一提交，才能重跑草稿任务恢复上传并保留人工编辑的发布说明。要从不同提交创建草稿，需要新发布版本，或由维护者检查并处理未发布草稿；不要删除已公开版本来重用版本号。这些发布目标限制不影响普通分支推送的自动构建。若没有可用的成功自动构建，不会进入草稿任务；若附件上传中断，草稿可能不完整，应重跑失败任务并等待成功后再验收。中间 Actions 产物保留七天，过期后需先重跑同一提交的自动构建，再重新启动手动发布。选择来源后若自动构建被重跑或附件被替换，本次发布会失败，需重新启动手动发布以固定新的来源。

发布自动化的本地检查（不会访问 GitHub 或创建 Release）：

```sh
node --test tests/ci/test_cmp_release.cjs
python -m unittest discover -s tests/ci -p 'test_android_signing.py'
python -m unittest discover -s tests/ci -p 'test_app_config.py'
```

## CI 与检查

README 的实机图片与设置组件渲染分别维护，设备来源、渲染命令和输出路径见 [截图说明](../screenshots/cmp/README.md)。设置图更新后逐张检查，不能用控件渲染替代设备或 GPU 验收。

- [Android 工作流](../.github/workflows/android-ci-release.yml) 定义 Android 的自动构建与标签发布行为。
- [CMP builds and release draft](../.github/workflows/cmp-release.yml) 在 CMP 分支推送时自动构建 Android/Windows 安装包，标签推送不重复触发；手动运行汇总同一提交的成功自动构建及 iOS 手动构建产物，创建三端发布草稿。
- [手动多平台工作流](../.github/workflows/multiplatform-manual.yml) 仅由 `workflow_dispatch` 触发，可选 Android、Windows、Linux、Wasm 和 iOS；产物保留七天，不负责发布、部署或设备安装。
- 手动工作流须先存在于默认分支，之后才能选择其他分支运行。Android 签名任务只应运行可信代码，使用现有签名 Secrets；iOS 产物为未签名 ARM64 设备 IPA，仍须用户重签名及真机验收。
- 平台和工具版本、任务与产物路径以工作流源码为准，不在文档中复制一套版本表。

按改动选择相关检查：

```sh
./gradlew :shared:desktopTest -PenableAndroid=false
./gradlew :app:testDebugUnitTest :app:lintDebug
node --test tests/host/browser-activation.test.mjs
python -m unittest discover -s tests/ci -p 'test_*.py'
```

构建日志、报告和截图保存在被忽略的构建输出目录或仓库外。docs 维护持久的构建、架构与功能说明，不保存逐轮迁移记录、测试次数或历史产物校验值。
