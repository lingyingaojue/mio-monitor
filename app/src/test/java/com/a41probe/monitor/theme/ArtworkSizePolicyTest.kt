package com.a41probe.monitor.theme

import com.a41probe.monitor.ui.theme.ArtworkSize
import com.a41probe.monitor.ui.theme.legacyArtworkSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.11：角色立绘四档大小 + 旧 boolean 开关迁移的单测。
 */
class ArtworkSizePolicyTest {

    @Test
    fun fourSizesWithExpectedLabels() {
        assertEquals(4, ArtworkSize.entries.size)
        assertEquals(
            listOf("关闭", "小", "中", "大"),
            ArtworkSize.entries.map { it.label },
        )
    }

    @Test
    fun heightsAreOrderedAndMediumKeepsLegacySize() {
        assertEquals(0, ArtworkSize.OFF.heightDp)
        assertEquals(300, ArtworkSize.SMALL.heightDp)
        assertEquals(420, ArtworkSize.MEDIUM.heightDp)
        assertEquals(540, ArtworkSize.LARGE.heightDp)
        assertTrue(ArtworkSize.SMALL.heightDp < ArtworkSize.MEDIUM.heightDp)
        assertTrue(ArtworkSize.MEDIUM.heightDp < ArtworkSize.LARGE.heightDp)
    }

    @Test
    fun legacyBooleanMapsToMediumOrOff() {
        assertEquals(ArtworkSize.MEDIUM, legacyArtworkSize(show = true))
        assertEquals(ArtworkSize.OFF, legacyArtworkSize(show = false))
    }

    @Test
    fun invalidOrdinalFallsBackToMedium() {
        assertEquals(ArtworkSize.MEDIUM, ArtworkSize.of(99))
        assertEquals(ArtworkSize.of(ArtworkSize.LARGE.ordinal), ArtworkSize.LARGE)
    }
}
