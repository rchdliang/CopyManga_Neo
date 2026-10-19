package com.rchdliang.copymanga_neo.domain.model

/** 原作显示名称与筛选标识分开保存；旧字符串数据没有标识时仅展示名称。 */
data class ComicOriginalWork(val name: String, val pathWord: String? = null)
