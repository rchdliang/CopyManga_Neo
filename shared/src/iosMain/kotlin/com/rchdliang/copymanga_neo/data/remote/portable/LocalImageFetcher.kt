package com.rchdliang.copymanga_neo.data.remote.portable

import coil3.Uri
import coil3.fetch.Fetcher

actual fun platformLocalImageFetcherFactory(timeoutSeconds:()->Int):Fetcher.Factory<Uri>? = null
