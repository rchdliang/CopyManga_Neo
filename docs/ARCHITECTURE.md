# 架构说明

CMP 使用共享 Compose UI、领域模型和 portable 数据层。各平台负责系统能力与生命周期适配，业务仓库及下载调度只维护一套。构建和版本配置见 [构建说明](BUILDING.md)。

## 模块与依赖

| 位置 | 职责 |
| --- | --- |
| `shared/src/commonMain/.../domain` | 领域模型、仓库接口、阅读与下载契约 |
| `shared/src/commonMain/.../data` | App/Web 协议、仓库实现、认证、持久化、下载与导出 |
| `shared/src/commonMain/.../multiplatform/ui` | 共享页面、ViewModel、导航、阅读器与交互组件 |
| `shared/src/androidMain`、`desktopMain`、`iosMain`、`wasmJsMain` | HTTP 引擎、文件与凭据存储、系统能力适配 |
| `app` | Android Application/Activity、窗口宿主、下载与导出前台服务 |
| `desktopApp` | 桌面启动器、窗口与托盘生命周期 |
| `iosApp` | Xcode 工程、Swift/UIKit 宿主；浏览器入口位于 `wasmJsMain` |

依赖方向是 UI → domain ← data。领域接口不依赖 Android；共享代码通过平台接口或 expect/actual 调用系统能力。

## 数据图与生命周期

[PortableAppGraph](../shared/src/commonMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/app/PortableAppGraph.kt) 组装仓库、网络状态、缓存、下载器、导出器与图片加载器，并拥有它们的协程及资源生命周期。

Android 使用应用级 `AndroidAppContainer` 与 [AndroidGraph](../shared/src/androidMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/app/AndroidGraph.kt)，Activity 重建不会关闭下载图。其他宿主通过各自的 Graph 创建入口提供 `PortableAppServices`。关闭图时先取消并等待传输、导出回滚，再释放客户端和存储资源。

桌面在打开数据图之前取得规范化数据目录的进程锁。同目录的第二次启动仅通知现有窗口，不创建第二份仓库或缓存。锁文件不被替换或删除，由操作系统在进程退出时释放锁。

## App/Web 请求与账号

- `CopyMangaClient` 统一构建主机、请求头、表单、签名及响应校验。App、Web API 和 WWW 使用各自协议，不能只凭同名端点共用身份或响应假设。
- `PortableNetworkState` 保存配置与会话 generation；配置或账号改变后撤销旧请求，迟到响应不能回写新会话。
- `PortableUserRepository` 隔离 App/Web 会话，并处理同账号会话替代。身份、账号、内容种类参与缓存或任务归属，不能只用作品路径作为所有内容的键。
- Android App 身份默认值由设备信息提供，Web 使用对应 UA。WWW 的站点 Cookie 不进入 API 或图片 CDN；公开资源请求不附带账号凭据。
- HTTP 状态和业务 code 都参与成功判断；空结果、身份不匹配和不完整章节不能当作成功。下载授权、写操作不做隐式重复请求。
- 站点风控由共享门控处理。403/429、验证码或明确风控响应会暂停相关下载，不能自动改走另一请求模式绕过。

协议配置 `COPYMANGA_TAP_STRING` 与应用发布版本是两类独立配置；应用版本只来自根目录 `version.properties`。

## 存储、缓存与凭据

`DocumentStorage` 管理设置和结构化记录，`CacheStorage` 管理二进制内容。文件后端使用临时文件与原子发布，读写异常显式返回，避免把损坏文件当成空数据覆盖。

| 平台 | 数据与凭据 |
| --- | --- |
| Android | 持久数据在 `filesDir/shared-data`，普通图片缓存在 `cacheDir/shared-images`；凭据密文在 `noBackupFilesDir`，密钥由 Keystore 管理 |
| Desktop | 启动器解析应用数据目录；凭据通过平台存储能力处理，目录由单实例锁协调 |
| iOS | Application Support 保存数据，Keychain 保存凭据，文件选择权限使用 security-scoped bookmark |
| Browser | 设置使用 local storage，二进制内容使用 IndexedDB；账号凭据仅保留在内存 |

漫画下载记录需与实际完整文件一致；小说正文和插图由 `VerifiedPortableNovelCache` 校验。普通图片缓存与用户主动下载的内容分别管理。

小说标注和阅读状态隔离账号、请求模式、作品与分卷；更新同一范围标注时保留原身份和创建时间。混合下载列表使用区分漫画与小说的类型化任务键。

## 下载、续传与后台能力

共享 `DownloadManager` 负责排队、暂停、恢复、取消、风控与完成状态。先保存任务，再通过 `DownloadRuntime.awaitReady` 确认运行条件，之后才请求内容或授权。进程重新启动后，未完成队列保持暂停，由用户继续。

漫画按图片原子发布。完成回执保存来源摘要与文件长度；恢复时只复用来源和长度均匹配的完整文件，缺失、截断、来源变化和未完整发布的文件重新下载。暂停或等待网络时保留有效部分进度；网络恢复不会覆盖用户手动暂停意图。

| 平台 | 下载运行条件与网络约束 |
| --- | --- |
| Android | 前台服务与通知维持后台下载；仅 Wi-Fi 模式绑定系统验证的 Network、socket、DNS 和独立连接池 |
| Desktop | 随应用进程运行；当前标准 JVM 传输不支持严格 Wi-Fi 绑定，仅 Wi-Fi 模式拒绝下载并说明原因 |
| iOS | 当前实现依赖前台应用，进入后台停止传输；未启用系统后台 URLSession 下载；不支持严格 Wi-Fi 绑定 |
| Browser | 页面隐藏时暂停；浏览器不提供所需网络绑定，不能把连接提示等同于仅 Wi-Fi 保证 |

Android 的 `ForegroundServiceGate` 只有在服务进入前台并取得唤醒锁后才确认就绪。等待 Wi-Fi 时服务保留、唤醒锁释放；任务完成后退出服务。服务失败或非正常停止会暂停任务。旧服务不能撤销新服务的就绪确认。

网络绑定或限制设置变化时，先取消旧传输再选择新客户端。蜂窝混合 VPN 不视为纯 Wi-Fi；不能通过接口名称或“存在 Wi-Fi 网卡”推断路由已经受约束。

## 导出与系统交互

共享导出器维护任务、文件顺序、ZIP/CBZ 格式、重试和取消。读写租约避免导出与删除/重新下载同时修改源文件。失败只清理本次活动输出，成功输出保留；选中文件的 URI 不应被误当作普通文件路径。

Android 使用 SAF 和前台导出服务，临时图片分享通过 FileProvider。iOS 使用文件选择器及书签授权，单文件输出要求匹配空文件预约。浏览器依赖 File System Access 的真实写入流，不把触发一次链接点击当作保存成功。

浏览器分享、剪贴板、文件选择、通知和全屏操作通过显式用户确认点击启动需要用户激活的 API；取消后不得再启动过期操作。凭据内容不得进入诊断错误文本。Windows 敏感剪贴板适配会请求排除历史及云同步，但不保证其他程序无法读取当前剪贴板。

## 共享 UI 与维护边界

`CopyMangaApp` 与共享导航宿主统一页面和 ViewModel。系统返回、浏览器 Back、Escape 和平台手势接入共享返回分派；阅读器窗口状态由带生命周期的宿主租约协调。

页面布局、筛选、下载列表、小说字体与间距滑块等放在 commonMain。只有权限、文件选择、窗口、系统通知等真实平台差异留在适配层，不为同一个通用控件维护多套平台文件。

修改协议或下载行为时同时检查 App/Web 路径、取消与迟到响应、账号归属及本地文件状态。平台未提供的能力应明确报告，不用模拟成功掩盖缺失。编译和单元测试不能替代设备权限、后台执行、真实网络或原生安装包验收。
