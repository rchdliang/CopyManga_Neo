# CMP 截图与渲染图

根目录 [README](../../README.md#界面截图) 将实机界面与设置组件渲染分开展示。

页面或控件改动后，应主动核对对应图片。发现截图与当前界面不一致时，明确提醒并重新采集或渲染；需要设备或素材才能更新时，说明缺少的条件，不以“旧版截图”的备注代替更新。

## 平板实机截图

本组图片于 2026-11-01 从已登录测试账号的 Android 平板采集：设备型号 `24018RPACC`，Android 16 / API 36，横屏原始分辨率 3048×2032。安装的 CMP 应用版本为 `0.10.30-d3b3d30`，平台升级编号为 39。

`android-tablet-*.png` 共九张，覆盖首页、搜索、漫画推荐、专题、详情、漫画书架、个人页、漫画双页和小说双栏阅读。图片直接来自设备截图，不改写界面文字或替换内容；README 只控制展示宽度。封面、作品信息及阅读片段属于各自权利方，仅用于展示客户端。

采集时使用测试账号，避免打开凭据、网络身份导出或私人诊断内容。需要临时调整阅读模式时先记录原设置，采集后恢复；更新图片前检查加载状态、工具栏、弹窗、选区和提示是否影响展示。实机页面中的系统动态配色、内容列表和更新时间可能随设备及上游服务变化。

## 本地设置渲染

`render-reader-settings.png`、`render-reader-display.png` 和 `render-reader-quality.png` 直接渲染当前生产组件：`ReaderSettingsDialog`、`ReaderDisplaySettings`、`ReaderQualityPreviewPanel` 与 `ReaderQualityControls`。采用固定浅色主题，显示设置使用双页示例，画质面板使用 Restore / M 示例；不读取账号，不下载漫画，也不启动 GPU 推理。

从仓库根目录运行：

```sh
./gradlew :shared:desktopTest --tests '*ReadmeReaderSettingsRenderTest' -PenableAndroid=false
```

Windows 使用 `.\gradlew.bat`。输出位于 `shared/build/reports/readme-settings/`；逐张检查文字、选中状态和边界后，将三张 PNG 复制到本目录并加上 `render-` 前缀。不要以本地渲染图替代设备截图，也不要用控件渲染证明 Vulkan 的实际画质、性能或驱动兼容性。
