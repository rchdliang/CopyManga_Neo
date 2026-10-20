# 构建与版本配置

项目结构与平台能力见 [架构说明](ARCHITECTURE.md)。以下命令从仓库根目录执行；Windows 使用 `.\gradlew.bat`，macOS/Linux 使用 `./gradlew`。

## 环境准备

- 使用完整 JDK 21 和仓库自带的 Gradle Wrapper。Gradle 版本以 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 为准；依赖版本以 [版本目录](../gradle/libs.versions.toml) 和各模块构建脚本为准。
- Android SDK 平台与 Build Tools 版本以 [app](../app/build.gradle.kts) 和 [shared](../shared/build.gradle.kts) 配置为准。在本地 `local.properties` 设置 `sdk.dir`，或设置 `ANDROID_HOME`。
- 桌面安装包使用对应操作系统及架构的 JDK；Windows EXE 安装向导需要 Inno Setup 6.5+，Linux DEB/RPM 需要系统打包工具。Windows 编译器可放入 PATH，也可通过环境变量 `ISCC` 指定 `ISCC.exe` 完整路径；标准的 Inno Setup 6 安装目录会自动检测。
- iOS 构建需要 macOS 和 Xcode；直接安装需要签名。未签名设备 IPA 可交给 SideStore / AltStore 使用用户自己的账号重签名。
- 浏览器打包需要 Node.js、Yarn Classic 和生产优化所用的 Binaryen `wasm-opt`。可参考 [手动 CI](../.github/workflows/multiplatform-manual.yml) 的工具安装步骤。
- 内存与并发默认值见 [gradle.properties](../gradle.properties)。内存受限时使用 `--max-workers=1`，不要同时运行多个平台构建。

Android Studio 打开仓库根目录，完成 Gradle Sync 后选择 `app` 运行配置。修改构建脚本后重新同步；不要在仍运行的构建中改动输入文件。

## 修改版本号

只编辑根目录的 [version.properties](../version.properties)：

| 字段 | 含义 |
| --- | --- |
| `COPYMANGA_VERSION_BASE` | 三段基础版本，如 `0.10.19`；用于 iOS 短版本 |
| `COPYMANGA_VERSION_HOTFIX` | 可选修订号；填 `1` 时完整显示版本为 `0.10.19.1`，留空时使用基础版本 |
| `COPYMANGA_VERSION_CODE` | 递增的整数构建号，用于 Android、iOS 与共享界面 |

这个文件的赋值语法同时适用于 Java Properties 和 Xcode 配置，不要在其中放本地路径、密钥或机器专用设置。

- 根 Gradle 脚本组合完整版本名，Android、共享界面及桌面 JAR 使用该版本；Linux 安装包沿用完整版本。
- Windows Inno Setup 安装向导显示完整版本（含修订号）；Compose 内部应用镜像仍使用“基础版本前两段 + 构建号”的三段版本。iOS 使用三段基础版本和构建号，应用内仍显示完整版本；不需要另改平台版本常量。
- `:shared:generateReleaseVersion` 自动生成共享 Kotlin 常量，所有 Kotlin 目标的编译任务自动依赖它。生成文件位于 `shared/build/generated/releaseVersion/commonMain`，不要手工编辑。
- iOS 的 [Config.xcconfig](../iosApp/Config.xcconfig) 直接包含同一文件，将其映射到 `MARKETING_VERSION` 与 `CURRENT_PROJECT_VERSION`；Debug/Release 共用配置。
- Android Debug 与 Release 使用相同版本名，不再追加测试后缀。
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

CMP Debug 与 Release 不再作为两个应用并存；签名相同且版本满足要求时会覆盖安装并使用同一份数据。切换调试签名与发布签名前先备份个人数据。旧的 `.migrationtest` 安装不会自动迁入新包。设备测试使用 `:app:connectedDebugAndroidTest`，需先连接目标设备；构建成功不等于设备操作验证完成。

## 桌面

Windows 下在 IntelliJ IDEA / Android Studio 打开项目并完成 Gradle 同步后，在顶部运行配置下拉框选择 **Windows Desktop**，点击 ▶ Run 即可启动桌面窗口，无需选择 Android 设备。共享配置位于 `.run/Windows Desktop.run.xml`，执行 `:desktopApp:run -PenableAndroid=false`，由项目工具链使用 JDK 21。若配置尚未出现，重新打开项目以加载共享运行配置。

```sh
./gradlew :shared:desktopTest :desktopApp:compileKotlin -PenableAndroid=false
./gradlew :desktopApp:run -PenableAndroid=false
```

`-PenableAndroid=false` 排除 Android 应用模块，适用于桌面、浏览器及独立 iOS 构建。Linux 使用 `:desktopApp:packageDeb`、`:desktopApp:packageRpm`，输出位于 `desktopApp/build/compose/binaries`。

Windows 安装包在对应架构的 Windows / JDK 21 上构建：

```powershell
.\gradlew.bat :desktopApp:packageInnoSetup -PenableAndroid=false --no-daemon
```

任务先生成包含运行时的 Compose 应用镜像，再编译 [Inno Setup 脚本](../desktopApp/installer/CopyMangaNeo.iss)。输出为 `desktopApp/build/installer/CopyMangaNeo-<完整版本>-windows-<架构>-setup.exe` 和 SHA-256 文件；x64 / ARM64 由构建 JDK 架构确定。CI 的 Windows 任务生成 x64 安装包，不再生成旧的 MSI / jpackage EXE。

安装向导提供简体中文／英文，允许选择目录，默认安装到当前用户的 `%LOCALAPPDATA%\Programs\CopyMangaNeo`，创建开始菜单入口；桌面快捷方式可选，完成页默认勾选「运行 CopyManga Neo」，静默安装不启动应用。安装程序使用 `desktopApp/icons/copymanga-neo.ico`，快捷方式与卸载入口使用应用 EXE 图标。简体中文资源固定来自 [Inno Setup is-6_7_1](https://github.com/jrsoftware/issrc/blob/is-6_7_1/Files/Languages/Unofficial/ChineseSimplified.isl)，保留译者声明。

首次从旧 MSI / jpackage EXE 切换时，先备份个人数据，并从 Windows 设置卸载旧程序，再安装新包；两种安装器的卸载记录不同，不能视为自动迁移。后续 Inno Setup 版本使用固定 AppId，直接覆盖安装。卸载脚本只移除安装文件，不主动删除应用数据。安装向导、快捷方式、完成页启动、卸载与跨版本覆盖安装仍需在 Windows 实机验收，编译成功不能替代这些检查。

仅制作需要外部 JDK 的便携 JAR 时，可以选择 Windows 原生库：

```sh
./gradlew :desktopApp:packageUberJarForCurrentOS -Pcopymanga.desktopTarget=windows-x64 -PenableAndroid=false
```

ARM64 改用 `windows-arm64`。输出位于 `desktopApp/build/compose/jars`，文件名包含配置版本；使用匹配架构的 JDK 21 运行 `java -jar`。跨平台选择仅用于打包，不能在 Linux 执行 Windows 原生代码或生成 Windows 安装程序。

打包前置任务会检查重复类、服务描述符、签名块和多版本 JAR 语义；不要绕过检查来强行合并依赖。诊断文件位于 `desktopApp/build/reports/portable-jar`。

桌面默认关闭开发专用行为。需要调试界面时，把 `-Dcopymanga.debug=true` 传给应用 JVM，而不是仅传给 Gradle。数据目录可通过应用 JVM 的 `-Dcopymanga.dataDir=...` 指定；不要让多个进程同时使用同一目录。

## iOS

1. 在 Apple Silicon Mac 上打开 `iosApp/iosApp.xcodeproj`。
2. 选择 `CopyMangaNeo` scheme 和 ARM64 模拟器或实体设备。
3. 实体设备选择自己的签名团队，然后 Build/Run。项目构建阶段会调用 `:shared:embedAndSignAppleFrameworkForXcode`。
4. 不构建 Android 应用时，给 Xcode 启动环境设置 `ORG_GRADLE_PROJECT_enableAndroid=false`。

App 模式配置可通过复制 `iosApp/LocalSecrets.xcconfig.example` 为被 Git 忽略的 `iosApp/LocalSecrets.xcconfig` 提供。Debug 也支持进程环境变量 `COPYMANGA_TAP_STRING`。通过构建设置写入 Info.plist 的值可从应用包中读取，不应存放个人密码或账号 token。

`:shared:compileKotlinIosArm64` 只验证共享 KLIB；Swift 编译、框架链接、签名、系统选择器和设备运行需要 Xcode 环境验证。

未签名真机发行包（JDK 21、macOS/Xcode，在仓库根运行）：

```sh
bash .github/scripts/build-ios-ipa.sh
```

脚本构建 Release / iphoneos / arm64，验证 Mach-O 平台、Bundle ID、版本和随包资源，再生成 `build/CopyMangaNeo-v<版本>-ios-arm64-unsigned.ipa`、SHA-256 与提交/版本清单。不会上传、签名或发布。CI 的 iOS 选项执行相同脚本，Artifact 可下载后交给 SideStore / AltStore Classic 重签名安装；模拟器包不能改名充当 IPA。

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

## CI 与检查

- [Android 工作流](../.github/workflows/android-ci-release.yml) 定义 Android 的自动构建与标签发布行为。
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

构建日志、报告和截图保存在被忽略的构建输出目录或仓库外。docs 仅维护本文件与架构说明，不保存逐轮迁移记录、测试次数或历史产物校验值。
