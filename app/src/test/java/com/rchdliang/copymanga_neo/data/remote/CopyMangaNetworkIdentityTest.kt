package com.rchdliang.copymanga_neo.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import com.rchdliang.copymanga_neo.data.remote.portable.CopyMangaNetworkConfig

class CopyMangaNetworkIdentityTest {
    @Test fun installationVariantDoesNotChangeExistingWebRequestIdentity() {
        val expected = "com.rchdliang.copymanga_neo"
        assertEquals(expected, CopyMangaNetworkConfig.apiHeaders()["X-Requested-With"])
        assertEquals(expected, CopyMangaNetworkConfig.noAuthApiHeaders()["X-Requested-With"])
        assertEquals(expected, CopyMangaNetworkConfig.chapterCatalogHeaders("test", "0")["X-Requested-With"])
    }
}
