<div align="center">

  <h1>CopyManga Neo</h1>
  拷貝漫畫的第三方 Android 客户端，使用 Kotlin + Jetpack Compose 构建<br><br>

![GitHub repo size](https://img.shields.io/github/repo-size/rchdliang/CopyManga_Neo)
![GitHub Repo stars](https://img.shields.io/github/stars/rchdliang/CopyManga_Neo)
![GitHub all releases](https://img.shields.io/github/downloads/rchdliang/CopyManga_Neo/total)

</div>

<br/>

## 说明

漫画数据均来源于[拷貝漫畫](https://2026copy.com/)官方，作者不对其中呈现的任何内容负责。

本应用使用了官方的网站的api，由于个人能力有限，未能获取到所有api（如评论功能）。

同时，本项目是Vibe Coding的产物，无法保证应用的稳定性，也无法承诺后续的维护更新。如有问题，请使用官方的客户端。

## 功能

- [x] **首页** — 推荐漫画、热门更新、最新上架、完结漫画
- [x] **搜索** — 按漫画名称、作者、汉化组搜索
- [x] **详情** — 漫画信息、章节列表、阅读进度
- [x] **阅读器** — 全屏阅读、翻页控制、手势操作、横竖屏切换、阅读模式设置
- [x] **书架** — 收藏/取消收藏，书架管理
- [x] **账号** — 用户登录、退出、个人信息
- [ ] **下载** — 离线下载（开发中）

## 预览

<div align="center">
  <em>截图待补充</em>
</div>

<br/>

## 技术栈

| 类别    | 技术                                     |
|-------|----------------------------------------|
| 语言    | Kotlin 2.3                             |
| UI 框架 | Jetpack Compose (Material3)            |
| 架构    | Clean Architecture（ui → domain → data） |
| 依赖注入  | Dagger Hilt + KSP                      |
| 网络    | Retrofit 3 + OkHttp 5                  |
| 序列化   | Gson                                   |
| 图片加载  | Coil                                   |
| 导航    | Navigation Compose                     |
| 持久化   | DataStore Preferences                  |
| 构建    | Gradle Kotlin DSL (AGP 9.2)            |
| 最低支持  | Android 12 (API 31)                    |


## 声明

此项目是个人为了学习而开发，仅用于学习和测试，请于下载后 24 小时内删除。
所有漫画内容版权归原作者及拷貝漫畫平台所有。
所用API皆从官方网站收集，不提供任何破解内容。
在此致敬[fumiama/copymanga](https://github.com/fumiama/copymanga)
