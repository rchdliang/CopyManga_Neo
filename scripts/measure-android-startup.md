# Android 启动与按需计时

## 初始化顺序

1. 恢复待处理的个人数据备份，再读取应用设置并发布加载页主题。
2. 恢复首页屏蔽规则、阅读记录、请求模式、账号凭据和题材缓存，创建共享数据图。
3. 显示正式导航和目的页面。目的页面完成第一次 Compose 绘制后，才预热阅读偏好、阅读工具、小说偏好、标注和日志。
4. 阅读设置的首次订阅、阅读器入口及设置备份会自行等待所需数据；系统服务索取数据图时会启动后台预热，不依赖 Activity。

账号资料刷新和首页角标同步在首帧之后启动，缓存身份和头像仍立即提供。首页保持原来的路由激活启动时机，后台标签和返回手势预览不会提前请求。首页漫画/小说请求准备及解析使用后台线程，静态占位卡使用普通 Row，真实数据仍使用原来的 LazyRow 和滚动状态。

Android 加载页和主页都提供 `AndroidPlatformHost`，使用 `CopyManga_NeoTheme` 的系统动态配色及已保存的深浅色模式。系统启动画面只等应用主题设置，不等整个数据图；没有人为延迟。

## 网络诊断日志

- 开关和当前分段编号独立存入 `network-log-settings.json`。
- Android / Desktop 文件后端使用 `CREATE + APPEND`，每次仅追加一条 JSONL 事件，不读取或重写历史。
- 固定 16 个分段，每段最多 256 KiB；轮换时删除最旧一段，总量不超过约 4 MiB。键值存储后端只替换当前有界分段。
- 初始化读取小型设置和文件大小，不解析日志正文。旧 `network-diagnostics.json` 正文直接删除，只从头部保留开关值，不迁移日志。
- 初始化前的请求先进入有界内存队列。用户主动导出时逐段读取，跳过中断写入留下的不完整记录。开关、轮换、追加、清除和导出由同一把锁协调。

## 探针

默认 Debug 和 Release 都使用 `startupNoop` 的空实现，不编入计时、日志和阶段集合实现，不增加测量/绘制节点。共享源码保留空接口和插桩位置，方便以后排查。仅显式传入 `-PenableStartupProbes=true` 时，才编入 `startupProbes` 中的 `AndroidStartupTrace` 并输出 `StartupTiming`；开关同时适用于 Debug 和 Release，不改变 Release 的压缩、资源裁剪、签名或可调试属性。每个进程每个阶段只记录一次，不包含账号、请求参数或正文。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease -PenableStartupProbes=true
```

不要把该参数写入 `gradle.properties` 或日常发布任务。诊断包与正常包使用相同安装标识，应独立保存并标清用途。去掉参数重新构建即可恢复普通包；不能通过应用设置或运行时 Intent 开启探针。

- `totalMs`：从 Android 进程启动到该阶段的累计时间。
- `durationMs`：对应初始化步骤自身的耗时。并发步骤不能直接相加。
- `cpuMs`：同步片段在当前线程实际消耗的 CPU 时间；`thread` 标注执行线程。墙钟时间与 CPU 时间之差不能直接等同于网络或 GPU 等待。
- `*_compose` / `*_subcompose` / `*_measure` / `*_place` / `*_draw_work`：首次组合、子组合、测量、放置与绘制执行时间。父阶段包含子阶段，不能叠加计算。
- `core_ready` / `graph_published`：数据图就绪 / 已交给界面。
- `loading_draw`：独立加载页绘制完成；如果启动足够快直接进入主页，此项可以不存在。
- `shell_draw`：应用根容器绘制完成，可能尚未显示初始导航目的页面。
- `home_loading_draw`：首页布局首次绘制，内容仍在请求中。
- `home_first_draw`：首页首次绘制，不要求此时仍处于加载状态。
- `home_requests_start` / `home_novel_requests_start`：后台线程实际开始漫画/小说请求准备。
- `home_comics_response` / `home_novel_response`：网络和解析完成；`home_comics_result_main` 标记结果返回主线程。
- `home_route_effect`：首页路由激活回调，触发尚未启动的首页请求。
- `session_model` / `favorites_model`：对应对象构造时间；`session_refresh_start` 和 `member_request_worker` 验证联网刷新已后置且离开主线程。
- `home_content_draw` / `home_error_draw`：首页数据状态 / 错误状态绘制；不表示全部封面图片加载完成。
- `background_start` / `background_ready`：后置初始化开始 / 完成。
- `palette=loading` / `palette=shell`：两处的主色、表面色，用于校验主题一致性。

绘制探针表示 Compose 绘制回调完成，不等同于 GPU 已呈现。`am start -W` 的 `TotalTime` 是系统启动统计，可能只覆盖加载页，应与首页探针分别比较。

## 复测

先安装显式启用探针的 Debug 或 Release APK，保持设备解锁。脚本只重启 CMP 包，不清应用数据、不清全局日志、不改设备设置；不要在下载或导出任务正在运行时手动执行冷启动基准。

```powershell
.\scripts\measure-android-startup.ps1 -Serial '<adb devices -l 中的序列号>' -Runs 5 -CooldownSeconds 10 -OutputDirectory build\reports\startup\my-run
```

默认排除一次预热，再收集五次强制停止后的进程冷启动。文件系统缓存保留，并非重启设备后的首次启动。结果含原始探针、系统启动输出及 `measurements.json`；前后比较使用同一设备、同一构建类型及相同轮次的中位数。

Debug 数字不能直接作为 Release 性能结论。比较构建类型时，必须使用同一份源码、同一份设置与用户数据、相同探针配置，并分别预热。若签名不同，可卸载重装后导入备份（备份不含登录凭据），或仅给本地测量副本重签名以便保留登录和下载；不要覆盖正式发布产物。记录 APK 哈希、实际 DEBUGGABLE 标志和测试顺序。首页数据时间还受网络响应影响。
