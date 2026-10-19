# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 导出任务和章节快照跨进程以 Gson 保存，字段名在混淆构建中必须保持稳定。
-keep class com.rchdliang.copymanga_neo.domain.model.ComicExport** { *; }

# 漫画卡片缓存由 Gson 反射读写；保留字段名及 List<Theme> 的元素类型。
-keep class com.rchdliang.copymanga_neo.domain.model.ComicCardMetadata { *; }
-keep class com.rchdliang.copymanga_neo.domain.model.Theme { *; }