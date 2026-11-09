package com.rchdliang.copymanga_neo.multiplatform.platform

import java.nio.file.Path

/** 各启动器使用同一资料目录规则，避免切换运行方式后产生第二份书库。 */
fun desktopDataDirectory(
    osName: String = System.getProperty("os.name"),
    home: String = System.getProperty("user.home"),
    override: String? = System.getProperty("copymanga.dataDir"),
    environment: (String) -> String? = System::getenv,
): Path {
    override?.takeIf(String::isNotBlank)?.let { return Path.of(it) }
    return when {
        osName.startsWith("Windows", true) ->
            Path.of(environment("LOCALAPPDATA")?.takeIf(String::isNotBlank) ?: home, "CopyMangaNeo")
        osName.startsWith("Mac", true) -> Path.of(home, "Library", "Application Support", "CopyMangaNeo")
        else -> Path.of(environment("XDG_DATA_HOME")?.takeIf(String::isNotBlank) ?: "$home/.local/share", "copymanga-neo")
    }
}
