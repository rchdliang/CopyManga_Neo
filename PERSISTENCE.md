# 首发后的本地数据契约

Android 宿主的 Debug / Release 均使用 `com.rchdliang.copymanga_neo`，不再增加 migrationtest 后缀，显示名称为 `CopyManga Neo`。原 Android 分支使用 `com.rchdliang.copymanga_neo.Android`，两个实现独立安装和存储；首发后保持这些安装标识稳定。

普通文档由 DocumentStore 管理。公开后的磁盘格式为：

```json
{"format":"copymanga.document/app-settings.json","schemaVersion":1,"payload":{}}
```

示例 payload 仅演示信封，实际字段由对应文档的 serializer 和 validate 定义。

## 文档版本与迁移

- 每个文档独立维护 schemaVersion；不得使用应用构建号替代数据版本。
- 文件名及 format 是稳定身份；不兼容字段/枚举/语义变更必须递增版本，提供旧版本到下一版的纯 JSON 转换。
- DocumentStore 的 migrations 参数以起始版本为键，逐步迁移直到目标版本。转换不能发网络请求或修改其他文件。
- 先解码并校验完整结果，再保存原件到 `<key>.before-v<目标版本>`，最后原子提交。失败不发布新内存状态，原件可重试。
- 高版本、同版本未知字段、缺迁移步骤、损坏或超限一律阻止写入，不默认空值后保存。
- 未发布的裸 payload 只做版本 0 → 1 的信封封装；不承诺维护历史开发格式。
- 文档加载必须发生在消费者开始写入之前。跨文档操作需要恢复日志，不能把多个原子写当作一个事务。

保留 DocumentMigrationTest 中的高版本、缺迁移、中断与原件保护检查，后续每个公开格式保存脱敏样本。凭据与普通文档分开存放；平台实现及内存回退见下方说明。

## 用户数据备份与恢复

轻量设置备份使用 `copymanga-neo-settings` / version 2，可交换两分支共有字段，但不等于完整数据备份。当前 `PortableBackupCodec` 未导出/恢复逐漫画 `pageLayout`，也未保留翻页动画与配页边界；导入时这些值采用其默认值。需要保留这些设置时使用本分支的完整用户数据备份。用户数据备份采用 `copymanga-neo-user-data` / version 1 / storage `portable-v1`，CMP Android、iOS、桌面共用。原 Android 分支的 `android-v1` 只能由原分支恢复。

白名单：应用设置、逐漫画阅读偏好、小说偏好、逐小说插图模糊、小说标注、本地历史/位置/书签、待同步进度、阅读工具和屏蔽标签。排除网络身份、登录凭据、下载内容/队列、导出任务及日志。个人数据包含账号标识和标注正文，应由用户妥善保管。

用户文件上限 48 MiB，单文档上限 16 MiB。导入校验类型、字段、协议版本与固定清单；不接受任意磁盘路径。增加新用户数据文档时同步维护白名单、格式版本及往返测试。

恢复通过明确的用户确认覆盖全部白名单数据，重启前可取消。在 PortableAppGraph 创建任何仓库或后台消费者前：

1. prepared：暂存目标，当前会话不变。
2. applying：持久记录重启时的原始快照，再写目标。
3. committed：确认全部提交；即使清理日志失败，也不重放覆盖用户后续改动。
4. rolled-back：写入失败时恢复原始快照并提示。双重失败保留日志并阻止正常启动，修复存储后重试。

进程中断后的 applying 可以幂等重放。UserDataRestoreTest 检查中断、回滚、取消和提交标志；UserDataBackupTest 检查小说标注/偏好往返与凭据/下载排除。

完整恢复不重新绑定设备凭据或文件选择器权限。iOS 重签名、更换签名账号、覆盖安装及系统恢复仍需真实设备验收。

## 阅读设置与重置范围

- `comic-reader-settings.json` 保存全局显示默认值与按漫画键保存的 `ReaderPreferences`。页面布局 `pageLayout` 默认 `SINGLE`；方向、缩放和屏幕方向与布局一起保存为漫画快照。修改 A 漫画不改 B 漫画；本轮不迁移旧全局布局，也没有冻结 seed 或自动清空数据。
- “重置显示设置”写入当前全局方向/缩放/屏幕方向的快照，并明确将当前漫画布局设为 `SINGLE`；不删除条目，不清其他漫画、章节配页或全局工具。
- `reader-tools.json` 保存全局翻页动画、音量键翻页、连续跨章、评论、自动阅读间隔及图片加载偏好。`pairingRestarts` 在其中按章节键保存排序去重后的原图索引边界；“恢复默认配页”只清当前章。遗留工具 `pageLayout` 字段不是当前布局 UI 的读写来源。
- 锐化强度与重新开启时的恢复值也在 `reader-tools.json` 全局保存，默认关闭。同尺寸修复模式属于当前阅读界面的可恢复状态，默认关闭，不写入持久偏好。
- 原图锚点用于历史与书签，双页视觉位置不替代原图索引。当前修复页面身份和处理状态仅在内存中存在，不写入历史或备份。

对应源码：[`PortablePreferences.kt`](shared/src/commonMain/kotlin/com/rchdliang/copymanga_neo/data/local/portable/PortablePreferences.kt)、[`PortableReaderTools.kt`](shared/src/commonMain/kotlin/com/rchdliang/copymanga_neo/data/local/portable/PortableReaderTools.kt)、[`PortableBackupCodec.kt`](shared/src/commonMain/kotlin/com/rchdliang/copymanga_neo/data/local/portable/PortableBackupCodec.kt)、[`PortableUserDataBackup.kt`](shared/src/commonMain/kotlin/com/rchdliang/copymanga_neo/data/local/portable/PortableUserDataBackup.kt)。完整用户数据备份包含上述漫画偏好和阅读工具文档；轻量导入的逐漫画回滚仅还原仍等于该次导入值的记录，保护导入后的用户编辑。

## 画质修复资源与结果

- Anime4K Restore S / Soft S 的 GLSL 和权重随 CMP Android 资源包分发，无运行时模型下载、原生运行库提取或推理临时文件。
- 输出按源位图身份、generationId、页面与模型缓存在阅读会话内存中，最多两份输出、总输出位图最多 16 MiB；源位图、GPU 纹理和活跃绘制引用另计。
- 后台/非活动路由清空缓存及 GPU 资源，会话关闭释放专用线程；修复结果不在备份白名单中。
- 老版本已经存放的超分缓存不由此次源码修改自动删除，且不再参与运行与打包。原图缓存、下载与用户数据不受影响。
- 实现范围和未验证限制见 [画质修复说明](docs/READER-RESTORATION.md)。

## 平台存储边界

- Android：普通文档在 `filesDir/shared-data/settings`，下载等内容在 `filesDir/shared-data/content`，图片缓存另在 `cacheDir/shared-images`。凭据使用 Android Keystore 与 AES-GCM，密文在 `noBackupFilesDir/shared-credentials.bin`。
- Windows：普通数据由桌面宿主的数据目录管理；凭据通过当前用户 DPAPI 加密后存盘。Linux 使用 Secret Service/libsecret，不以普通明文文件替代；安全存储工具不可用时，桌面宿主只保留本次会话的内存凭据并提示用户。
- iOS：普通数据在 Application Support 下的 `CopyMangaNeo` 目录，凭据使用 Keychain。这里只说明实现，不代表已完成实机验证。
- Web/Wasm：设置使用 local storage，二进制内容使用 IndexedDB；容量、清理与持久性受浏览器策略影响。凭据只在内存中保留到页面关闭，不承诺持久登录。

对应入口：`shared/src/{androidMain,desktopMain,iosMain,wasmJsMain}/kotlin/com/rchdliang/copymanga_neo/multiplatform/app/` 下的平台 Graph，以及各平台的安全凭据适配器。维护计划调整不改变包名、文档身份或备份格式，也不触发自动清空、跨分支迁移或恢复。
