# 首发后的本地数据契约

Android 宿主的 Debug / Release 均使用 `com.rchdliang.copymanga_neo`，不再增加 migrationtest 后缀，显示名称为 `CopyManga_Neo`。原 Android 分支使用 `com.rchdliang.copymanga_neo.Android`，两个实现独立安装和存储；首发后保持这些安装标识稳定。

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

保留 DocumentMigrationTest 中的高版本、缺迁移、中断与原件保护检查，后续每个公开格式保存脱敏样本。敏感信息仍由系统 Keychain/Keystore/DPAPI/Secret Service 管理，不经过普通文档。

## 用户数据备份与恢复

轻量设置备份保持原有可互通格式。用户数据备份采用 `copymanga-neo-user-data` / version 1 / storage `portable-v1`，CMP Android、iOS、桌面共用。原 Android 分支的 `android-v1` 只能由原分支恢复。

白名单：应用设置、逐漫画阅读偏好、小说偏好、逐小说插图模糊、小说标注、本地历史/位置/书签、待同步进度、阅读工具和屏蔽标签。排除网络身份、登录凭据、下载内容/队列、导出任务及日志。个人数据包含账号标识和标注正文，应由用户妥善保管。

用户文件上限 48 MiB，单文档上限 16 MiB。导入校验类型、字段、协议版本与固定清单；不接受任意磁盘路径。增加新用户数据文档时同步维护白名单、格式版本及往返测试。

恢复通过明确的用户确认覆盖全部白名单数据，重启前可取消。在 PortableAppGraph 创建任何仓库或后台消费者前：

1. prepared：暂存目标，当前会话不变。
2. applying：持久记录重启时的原始快照，再写目标。
3. committed：确认全部提交；即使清理日志失败，也不重放覆盖用户后续改动。
4. rolled-back：写入失败时恢复原始快照并提示。双重失败保留日志并阻止正常启动，修复存储后重试。

进程中断后的 applying 可以幂等重放。UserDataRestoreTest 检查中断、回滚、取消和提交标志；UserDataBackupTest 检查小说标注/偏好往返与凭据/下载排除。

完整恢复不重新绑定设备凭据或文件选择器权限。iOS 重签名、更换签名账号、覆盖安装及系统恢复仍需真实设备验收。
