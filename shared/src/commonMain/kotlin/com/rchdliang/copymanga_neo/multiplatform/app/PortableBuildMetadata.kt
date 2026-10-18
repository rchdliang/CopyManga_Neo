package com.rchdliang.copymanga_neo.multiplatform.app

import com.rchdliang.copymanga_neo.multiplatform.ui.profile.AppBuildInfo

/** 发布版本来自根目录配置；开发专用行为仍由宿主显式启用。 */
val portableReleaseBuildInfo = AppBuildInfo("CopyManga Neo", ReleaseVersion.NAME, ReleaseVersion.CODE, "com.rchdliang.copymanga_neo", false)
