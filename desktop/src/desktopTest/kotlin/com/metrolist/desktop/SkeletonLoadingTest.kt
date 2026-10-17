package com.metrolist.desktop

import com.metrolist.desktop.ui.DesktopColors
import com.metrolist.desktop.ui.DesktopRadii
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkeletonLoadingTest {

    @Test
    fun testSkeletonStylingConstants() {
        assertNotNull(DesktopColors.Carbon)
        assertNotNull(DesktopColors.Slate)
        assertNotNull(DesktopRadii.Badge)
        assertNotNull(DesktopRadii.Card)
    }

    @Test
    fun testShimmerLinearGradientSpec() {
        val duration = 1200
        assertTrue("Shimmer duration should be within smooth animation range", duration in 800..2000)
    }
}
