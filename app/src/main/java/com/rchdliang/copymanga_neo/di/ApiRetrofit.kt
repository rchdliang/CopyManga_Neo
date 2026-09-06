package com.rchdliang.copymanga_neo.di

import javax.inject.Qualifier

/**
 * 限定符，用于标识 API 专用的 Retrofit 实例
 * 使用 api.copy4000.com 域名
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiRetrofit

/**
 * 限定符，用于标识 WWW 专用的 Retrofit 实例
 * 使用 www.copy3000.com 域名，用于获取 cookies
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class WwwRetrofit
