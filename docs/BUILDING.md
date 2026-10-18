# 构建与版本配置

项目结构与平台能力见 [架构说明](ARCHITECTURE.md)。以下命令从仓库根目录执行；Windows 使用 `.\gradlew.bat`，macOS/Linux 使用 `./gradlew`。

## 环境准备

- 使用完整 JDK 21 和仓库自带的 Gradle Wrapper。Gradle 版本以 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 为准；依赖版本以 [版本目录](../gradle/libs.versions.toml) 和各模块构建脚本为准。
- Android SDK 平台与 Build Tools 版本以 [app](../app/build.gradle.kts) 和 [shared](../shared/build.gradle.kts) 配置为准。在本地 `local.properties` 设置 `sdk.dir`，或设置 `ANDROID_HOME`。
- 桌面安装包使用对应操作系统及架构的 JDK；Windows MSI/EXE 需要 WiX，Linux DEB/RPM 需要系统打包工具。
- iOS 应用需要 macOS、Xcode 和签名团队；当前模拟器目标是 ARM64。
- 浏览器打包需要 Node.js、Yarn Classic 和生产优化所用的 Binaryen `wasm-opt`。可参考 [手动 CI](../.github/workflows/multiplatform-manual.yml) 的工具安装步骤。
- 内存与并发默认值见 [gradle.properties](../gradle.properties)。内存受限时使用 `--max-workers=1`，不要同时运行多个平台构建。

Android Studio 打开仓库根目录，完成 Gradle Sync 后选择 `app` 运行配置。修改构建脚本后重新同步；不要在仍运行的构建中改动输入文件。

## 修改版本号

只编辑根目录的 [version.properties](../version.properties)：

| 字段 | 含义 |
| --- | --- |
| `COPYMANGA_VERSION_NAME` | 三段数值版本名，用于 Android、桌面安装包、iOS 与共享界面 |
| `COPYMANGA_VERSION_CODE` | 递增的整数构建号，用于 Android、iOS 与共享界面 |

这个文件的赋值语法同时适用于 Java Properties 和 Xcode 配置，不要在其中放本地路径、密钥或机器专用设置。

- 根 Gradle 脚本读取并校验两个字段；Android 与桌面模块直接使用读取结果。
- `:shared:generateReleaseVersion` 自动生成共享 Kotlin 常量，所有 Kotlin 目标的编译任务自动依赖它。生成文件位于 `shared/build/generated/releaseVersion/commonMain`，不要手工编辑。
- iOS 的 [Config.xcconfig](../iosApp/Config.xcconfig) 直接包含同一文件，将其映射到 `MARKETING_VERSION` 与 `CURRENT_PROJECT_VERSION`；Debug/Release 共用配置。
- Android Debug 自动追加 `-migrationtest`，不需要在版本源中填写后缀。
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

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，包名为 `com.rchdliang.copymanga_neo.migrationtest`，桌面名称为“CopyManga Neo 测试版”。它与原版应用的私有账号、设置和下载数据分开；导出目录、相册和用户选中的共享文件夹仍需自行区分。

App 请求模式所需的 `COPYMANGA_TAP_STRING` 从构建进程环境变量或本地 `local.properties` 读取，环境变量优先。没有该配置仍能构建，但 App 模式不可用。新 worktree 不会自动复制被忽略的本地配置，需要单独配置。不要把实际值写入源码、示例或日志。

Release 构建：

```sh
./gradlew :app:assembleRelease
```

需要本地 `keystore.properties` 的 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 及对应密钥库。普通本地 Debug 使用调试签名；`-PuseReleaseSigningForTest=true` 让独立测试包使用已有发布签名，签名材料不完整时构建失败。

相同包名只有相同签名才能覆盖安装。切换本地调试签名与 CI 发布签名可能需要先备份再重装测试版；不要卸载原版应用。设备测试使用 `:app:connectedDebugAndroidTest`，需先连接目标设备；构建成功不等于设备操作验证完成。

## 桌面

```sh
./gradlew :shared:desktopTest :desktopApp:compileKotlin -PenableAndroid=false
./gradlew :desktopApp:run -PenableAndroid=false
```

`-PenableAndroid=false` 排除 Android 应用模块，适用于桌面、浏览器及独立 iOS 构建。Windows 使用 `:desktopApp:packageMsi`、`:desktopApp:packageExe`；Linux 使用 `:desktopApp:packageDeb`、`:desktopApp:packageRpm`。输出位于 `desktopApp/build/compose/binaries`，安装包必须在匹配的平台验证。

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

## 浏览器

```sh
./gradlew :shared:wasmJsBrowserDevelopmentRun -PenableAndroid=false
./gradlew :shared:wasmJsBrowserDistribution -PenableAndroid=false
```

生产输出位于 `shared/build/dist/wasmJs/productionExecutable`。生产站点使用 HTTPS；本地开发可使用 localhost。开发和生产 webpack 任务请分开调用，避免共用 npm 输出产生冲突。

构建使用 PATH 中的 Node、Yarn 和 Binaryen；也可通过 `-Pcopymanga.nodeCommand=...`、`-Pcopymanga.yarnCommand=...`、`-Pcopymanga.binaryenCommand=...` 指定。保持自动工具下载关闭，以遵守设置中的仓库策略。

宿主可在应用脚本之前设置 `globalThis.copyMangaNeoConfig.tapString`。本地 `shared/src/wasmJsMain/resources/copy-manga-config.local.js` 被忽略，但需要由本地页面显式加载。浏览器配置对访问者可见，不应包含账号凭据；配置不会解除 CORS、禁用请求头或代理限制。

`gradle.properties` 中的 Wasm 增量链接配置及堆大小用于当前工具链。遇到旧链接缓存错误时，先停止构建，再只清理相关的生成链接缓存；不要删除源文件、账号数据或其他平台产物。

## CI 与检查

- [Android 工作流](../.github/workflows/android-ci-release.yml) 定义 Android 的自动构建与标签发布行为。
- [手动多平台工作流](../.github/workflows/multiplatform-manual.yml) 仅由 `workflow_dispatch` 触发，可选 Android、Windows、Linux、Wasm 和 iOS；产物保留七天，不负责发布、部署或设备安装。
- 手动工作流须先存在于默认分支，之后才能选择其他分支运行。Android 签名任务只应运行可信代码，使用现有签名 Secrets；iOS 产物为未签名 ARM64 模拟器应用，不是实体设备 IPA。
- 平台和工具版本、任务与产物路径以工作流源码为准，不在文档中复制一套版本表。

按改动选择相关检查：

```sh
./gradlew :shared:desktopTest -PenableAndroid=false
./gradlew :app:testDebugUnitTest :app:lintDebug
node --test tests/host/browser-activation.test.mjs
python -m unittest discover -s tests/ci -p 'test_*.py'
```

构建日志、报告和截图保存在被忽略的构建输出目录或仓库外。docs 仅维护本文件与架构说明，不保存逐轮迁移记录、测试次数或历史产物校验值。
