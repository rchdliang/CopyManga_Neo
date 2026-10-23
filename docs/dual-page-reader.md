# CopyManga Neo 双页阅读

## 基线与范围

2026-10-23 核实远端默认分支为 CMP。CMP 从 `098cb6b65005370edda90e86807712bfd85fc321` 开始；独立 Android 从 `9a0da27ad74528169000c021734a8bc2f54d64ec` 开始。实现仅在 `feat/dual-page-cmp` 与 `feat/dual-page-android` 本地工作树，未推送、建 PR、合并或运行 GitHub Actions。仓库及工作区未找到 AGENTS.md；未发现 Android Studio / IntelliJ MCP，使用工作区 Gradle、SDK、JDK。

未包含并行 UI 审查或 CPU/Vulkan 超分任务的未推送修改。没有改首页搜索、繁简转换文案或其他页面。

## 行为和入口

阅读器原有设置入口内增加页面布局分组，使用现有 Material 3 FilterChip、Switch、TextButton、OutlinedTextField。默认单页，避免改变已有阅读习惯；全局记住单页／双页／自动及动画选择。配页调整和说明默认折叠，不占满工具栏。页脚显示原图范围，例如 `2–3/20`；滑块和书签仍显示／保存选定原图页。

- 双页：可用内容宽度至少 600dp 且宽大于高；自动：至少 840dp 且宽大于高。窄屏、竖屏或纵向连续阅读暂用单页，保留用户的双页／自动选择。
- 默认封面（原图第 1 页）单独显示，随后相邻两张配对；余下一张独占一屏。双页按 Fit 保持比例，不拉伸、不裁切、不写入合成图片。
- 宽大于高的已加载图片独占一屏。尺寸来自解码后图片的 intrinsicSize，不做内容识别。尺寸未知的图片首次加载后可能触发一次保留原图位置的重排。
- LTR 左页在先，RTL 右页在先；原图顺序始终按服务端页序。RTL 同时反转视觉左右和水平滑动／箭头方向。PageDown、向下滚轮始终下一组，PageUp、向上滚轮始终上一组。
- 分组绝不跨章节或吞并章节评论。连续阅读、评论、章节窗口裁剪继续使用原列表。

## 解决扫描错配

“调整本章配页”中填写**原图页码（从 1 开始）**，点击“从此页重新配对”。该位置建立一条章内边界；从此页开始向后两两配对，直到下一条边界。边界之前若剩一页便单独显示。可以添加多个位置，修正章节中途广告、漏页造成的再次错位；没有仅用一个全章奇偶开关代替局部修正。

例如原图 1 为封面，默认 `1 | 2,3 | 4,5 | 6,7 | 8`。添加“第 3 页起”和“第 6 页起”后成为 `1 | 2 | 3,4 | 5 | 6,7 | 8`。可逐条移除，也可“恢复本章默认配页”。调整按章节 UUID 保存，重启后保留；只影响这一章。横图始终独占，手动边界也不会强塞横图与下一页。

可在第 1 页建立边界来配对没有封面的章节。图片增删刷新后，超出页数的边界被忽略；原始页、历史和书签不改写。

## 输入、动画与位置

触摸单击中间显示／隐藏工具栏；左右 15% 区域在未放大时翻组。双击以触点为焦点放大到 2.5 倍，再次双击复原；双指缩放范围 1–5 倍。两页作为一个视口共同缩放和平移，焦点不会在两张图间跳动。超过系统 touchSlop 后认作拖动，多指手势消费整个手势序列。

放大后单指只平移，即使拖到边缘也不自动翻页；边缘点击改为显示工具栏。需要双击／0 复原后再滑动翻页，或使用明确的 PageUp/PageDown。惯性中首次点击只刹车。没有设置 systemGestureExclusion，系统返回边缘仍由系统处理；真实设备的系统手势协调仍需实测。

分页阅读键盘：←／→ 按阅读方向翻组，PageUp/PageDown 上／下一组，空格下一组、Shift+空格上一组。＋／－（或 Ctrl+＋／－）缩放，0（或 Ctrl+0）复原。放大后四个箭头平移，滚轮／触控板二维滚动平移，Ctrl+滚轮以指针为焦点缩放。Alt/Meta 组合、无关 Ctrl 组合不拦截；仅阅读器自身焦点接收，输入框、按钮、设置和外部模态不接收阅读翻页快捷键。CMP 纵向阅读继续使用原键盘滚动和 Ctrl+滚轮路径。

翻页使用 180ms 轻量水平平移和原 Pager 跟手拖动。关闭动画会关闭程序翻页及松手定位动画，手指拖动仍跟手。不会做仿真卷页。变更布局／配页时保留原图位置，必要时复原缩放以适应新的视口；不恢复到章首。

## 代码边界和进度保证

`ReaderSpread.kt` 是无 UI 的纯分组算法，`ReaderSpreadContent.kt` 负责分页视口，`ReaderSpreadInput.kt` 负责分页输入，`ReaderSpreadOptions.kt` 负责设置。两条分支保持对应实现；CMP 位于 shared/commonMain，独立 Android 位于 app/main。

`pages` 始终为原始 ReaderPageEntry 列表；只有 Pager 的位置是分组索引。所有跳页先按章节＋原图索引解析，再映射到组；组内保留准确的原图锚点，尤其是跳入组内第二张。记录协程直接读取锚点状态，不能使用延迟一帧的派生副本。历史、书签、章节激活、导出／长按来源均仍用原图索引。只有两张都加载成功且整组停稳，末组才能完成章节；拖动期间不报告临时分组位置。

预加载从当前组最后一张之后开始，保持原设置的张数上限和 dispose 取消。缩放状态仅保留当前组与相邻两组，退出时停止惯性。图片尺寸元数据限当前章节窗口；无合图内存副本。

CMP 保留 Coil 3 和 ReaderImageRequestLayout：每张图按自己的物理像素视口申请解码尺寸，使用整个组的缩放倍率；原半倍档位、120ms 防抖、旧图保留、windows-cubic-v1 缓存标记均不变。独立 Android 使用现有 Coil 2，不引入 CMP 解码器或任何超分依赖。不要将半屏图片的解码请求改成整组宽度，也不要用低分辨率预载覆盖阅读图。

CMP 偏好由 PortableReaderTools 的序列化文档存储；独立 Android 通过 ReaderToolsStore SharedPreferences 提交并更新 ReaderToolsBackupCodec，旧备份缺失新字段时默认单页／开启动画／无局部调整。

## 集成冲突面

与后续 UI、超分工作可能重叠：两分支的 `ReaderScreen.kt`、`ReaderContent.kt`、`ReaderZoomGestures.kt`、`domain/model/ReaderTools.kt`；Android 另有 `data/local/ReaderToolsStore.kt` 和 `ReaderToolsBackupCodec.kt`。两分支仅将 app_name 改为产品显示名 CopyManga Neo。ReaderDialogs、ReaderControls、图像仓库、超分后端、WindowsReaderImageDecoder、ReaderImageQuality 本身均未修改。

合并超分时建议保持叶片的 ReaderImageItem 为入口，输入仍为原图路径、实际叶片约束和组缩放倍率；配页模块不应读取或生成超分位图。后续 UI 可移动 ReaderSpreadOptions 的入口，不应把原图页码替换为 Pager 页码。

## 验证及限制

算法测试覆盖 LTR/RTL、封面、奇数尾页、完整横图、多个局部调整、逐条恢复和全章恢复、章节／评论边界、全页映射及窗口阈值，并遍历 400 种横图与重配边界组合。CMP 另有生产阅读屏位置测试、键鼠焦点和修饰键测试、触摸双击／双指／平移测试、真实叶片高清解码与请求取消测试。独立 Android 另测旧偏好兼容与备份往返。

`reader-spread-runtime.png` 与 `reader-spread-settings-runtime.png` 是 Linux 软件渲染的实际 Compose 组件，使用合成测试图；不是 Android、Windows 实机截图，也不是应用端到端运行证明。位置测试使用预览图片，仅验证生产状态流；高清测试使用本地 PNG 和真实解码器。

最终验证成功（2026-10-23）：

```sh
# CMP：30 项选定回归测试，0 失败、0 跳过；同时构建 Android APK 和 Linux x64 桌面 JAR
./gradlew :shared:desktopTest --tests '*ReaderSpread*' --tests '*ReaderPositionTest*' \
  --tests '*WindowsReaderImageQualityTest*' --tests '*RouteEffectsSceneTest*' \
  :app:assembleDebug :desktopApp:packageUberJarForCurrentOS --console=plain
# 独立 Android：全部 169 项 JVM 单元测试，0 失败、0 跳过，同时构建 APK
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

CMP 的 30 项中，新双页测试 16 项，既有位置／生命周期／高清回归 14 项；独立 Android 包含 9 项新配页测试和 3 项偏好兼容测试。首次完整状态测试发现跳入第二张时短暂记为第一张，已修复并由最终测试验证。后续 UI 任务报告的 CMP `54ffdd4`、`73cbc09` 与 Android `64ef9ba`、`0605593`、`c1c1b60` 均未合入当前工作树。最终提交和产物校验值见交付目录中的验证报告。

没有 Android 设备／模拟器、Windows 主机或 GPU 实机验证。首次未知尺寸导致的重排、厂商返回手势、真实触控板惯性、长时间大图内存压力和超分集成仍需后续设备验证。未实现图像内容识别、自动裁图、永久合图或局部页序重排。
