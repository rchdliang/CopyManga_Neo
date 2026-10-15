package com.rchdliang.copymanga_neo.data.remote.portable

import coil3.Uri
import coil3.fetch.Fetcher

/** Browser blob URLs need bounded local reads; native file/content fetchers are provided by Coil. */
expect fun platformLocalImageFetcherFactory(timeoutSeconds:()->Int):Fetcher.Factory<Uri>?
