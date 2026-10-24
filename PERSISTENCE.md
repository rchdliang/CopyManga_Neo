# 首发后的本地数据契约

安装标识为 `com.rchdliang.copymanga_neo.Android`，显示名称为 `CopyManga Neo`。CMP 保留 `com.rchdliang.copymanga_neo`，两个实现独立安装、独立存储；代码 namespace 不随安装标识改名。首发后保持安装标识稳定。

首个公开 Android 数据库结构以 `app/schemas/com.rchdliang.copymanga_neo.data.local.db.AppDatabase/6.json` 为基线。保留这个快照，不回填未发布版本的数据迁移。

## 数据库升级

- 正式数据库禁止 destructive fallback，包括降级。变更实体时递增 schema 版本，提供完整迁移链，提交新增 schema。
- 不能修改已发布 schema 来让测试通过。维护首发 → 当前、相邻版 → 当前、跳版本的真实数据迁移测试。
- `PublishedDatabaseBaselineTest` 验证首发结构能打开、下载/评论/队列保留，以及更高版本不会被重建。
- 执行 `./gradlew :app:connectedDebugAndroidTest` 需要设备/模拟器；编译测试 APK 不等于执行迁移测试。

## 凭据

- DataStore 使用版本化 AES-GCM 容器；密钥来自 Android Keystore，密文只放在 noBackupFilesDir。
- Cookie 单独加密、原子提交。凭据、Cookie、设备身份不随系统备份迁移；换机后重新登录。
- 缺密钥、坏密文、未知格式都保留原件并报告错误，不使用空值覆盖，不生成替代密钥试图解密。
- 认证 schema 的更改必须编写显式转换，不允许遇到不匹配就 clear。首次空库才能初始化。
- 安全库不可读时，界面允许用户明确安排重置登录信息。请求在下次启动、DataStore 创建前执行，只删除凭据/Cookie 及其设备密钥，保留全部阅读与下载数据。
- 导出凭据是用户独立操作，不能混入个人资料备份。客户端协议配置不等同服务器秘密。

## 用户数据备份

轻量设置备份使用 `copymanga-neo-settings` / version 2，可交换两分支共有字段。原生 Android 的 `ComicPreferencesCodec` 包含漫画布局，`ReaderToolsBackupCodec` 包含翻页动画和按章配页；当前 CMP 轻量编解码器不保留这三项，因此跨分支往返并非全部设置无损。新增用户数据备份为 `copymanga-neo-user-data` / version 1 / storage `android-v1`，只恢复同一数据实现；CMP 的 `portable-v1` 不能直接导入。

固定清单包括应用/漫画/小说偏好、小说插图模糊偏好、小说标注、全部账号的本地历史与书签、待同步阅读进度、阅读工具及屏蔽标签。排除凭据、Cookie、网络配置、设备身份、下载内容、临时任务及日志。

DataStore 字节以 Base64 保存，SharedPreferences 以白名单 JSON 保存；格式使用固定字段而非反射模型。清单、字段类型或语义发生不兼容变更时必须提升备份协议并添加转换。用户文件最多 48 MiB；单项二进制数据最多 16 MiB。

恢复是明确的覆盖操作：先全量验证，再暂存至 noBackupFilesDir。用户可在重启前取消。应用 attachBaseContext 在 Hilt/后台消费者初始化前完成恢复：

1. prepared：只暂存目标数据，当前会话继续使用原数据。
2. applying：记录重启时的原始快照后写入目标数据。
3. committed：全部写入并确认。清理失败也不会再次覆盖后来写入的数据。
4. rolled-back：写入失败时还原原始快照，提示重新选择备份。

进程中断保留 applying 日志，下次启动先幂等重放。恢复和回滚均无法写入时停止初始化并保留日志，避免正常消费者继续写入混合数据；释放空间或修复存储后重启。

备份保留个人账号标识和标注正文，应由用户保管。不要把真实用户备份加入测试或提交仓库。

## 阅读设置与重置范围

- `ReaderPreferencesStore` 在偏好 DataStore 中，以 `comic_preferences:<漫画标识>` 保存方向、缩放、屏幕方向及 `layout`。新布局默认 `SINGLE`；初始化后是独立漫画快照，修改 A 不影响 B。本轮不迁移旧全局布局，也不在启动时自动清空数据。
- “重置显示设置”将当前漫画写为全局方向/缩放/屏幕方向的快照，布局为 `SINGLE`；不会删除其他漫画设置，也不重置章节配页和全局工具。
- `reader_tools` SharedPreferences 保存全局翻页动画、音量键翻页、连续跨章、评论、自动阅读间隔与图片加载偏好。`pairing_restarts` 按章节键保存排序去重后的原图索引边界；恢复默认配页只影响当前章。遗留 `page_layout` 不再是当前布局 UI 的来源。
- `ComicPreferencesCodec` 与设置备份保存布局字段；完整用户数据备份也包含偏好 DataStore 和阅读工具白名单。逐漫画设置导入回滚只还原仍等于该次导入值的记录，保护之后的用户编辑。完整用户数据恢复仍遵循前述重启覆盖流程。
- 阅读历史与书签保存真实原图锚点，不以双页的视觉序号替代。页面加载、跨章请求、手势和当前显示状态属于内存会话，不作为持久化偏好。
- 原生 Android 分支没有 CMP 的锐化和 AI 增强入口，也没有对应模型下载目录、推理缓存或增强会话设置。

对应源码：[`ReaderPreferencesStore.kt`](app/src/main/java/com/rchdliang/copymanga_neo/data/local/ReaderPreferencesStore.kt)、[`ComicPreferencesCodec.kt`](app/src/main/java/com/rchdliang/copymanga_neo/data/local/ComicPreferencesCodec.kt)、[`ReaderToolsStore.kt`](app/src/main/java/com/rchdliang/copymanga_neo/data/local/ReaderToolsStore.kt)、[`ReaderToolsBackupCodec.kt`](app/src/main/java/com/rchdliang/copymanga_neo/data/local/ReaderToolsBackupCodec.kt)、[`SettingsBackupCodec.kt`](app/src/main/java/com/rchdliang/copymanga_neo/data/local/SettingsBackupCodec.kt)。

原生 Android 计划近期停止维护，后续仅维护 CMP。这一计划不改变现有安装标识与数据契约，不自动迁移至 CMP，也不自动删除账号、书架、历史、书签或下载。
