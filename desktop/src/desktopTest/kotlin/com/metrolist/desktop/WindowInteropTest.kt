package com.metrolist.desktop

import com.jetbrains.JBR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Frame

class WindowInteropTest {

    @Test
    fun testJbrAvailabilityQueryDoesNotThrow() {
        val supported = runCatching { JBR.isWindowDecorationsSupported() }
        assertTrue("Querying JBR window decorations support must never throw", supported.isSuccess)
    }

    @Test
    fun testCustomTitleBarConfigurationWhenSupported() {
        if (!runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)) {
            println("Skipping test: JBR window decorations not available on current test runner JDK")
            return
        }

        val frame = Frame("Metrolist Test Window")
        try {
            val decorations = JBR.getWindowDecorations()
            assertNotNull("WindowDecorations service must not be null when supported", decorations)

            val titleBar = decorations.createCustomTitleBar()
            assertNotNull("CustomTitleBar instance must be created", titleBar)

            titleBar.height = 56f
            assertEquals(56f, titleBar.height, 0.001f)

            titleBar.putProperty("controls.visible", false)
            decorations.setCustomTitleBar(frame, titleBar)

            // Cleanup
            decorations.setCustomTitleBar(frame, null)
        } finally {
            frame.dispose()
        }
    }
}
