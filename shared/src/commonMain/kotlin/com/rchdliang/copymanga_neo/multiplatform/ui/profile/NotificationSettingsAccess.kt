package com.rchdliang.copymanga_neo.multiplatform.ui.profile

internal suspend fun openNotificationSettings(platform: ProfilePlatform): Result<Unit> =
    platform.openNotificationSettings()
