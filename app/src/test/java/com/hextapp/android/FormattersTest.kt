package com.hextapp.android

import com.hextapp.android.ui.components.formatBytes
import com.hextapp.android.ui.components.formatDownloads
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun formatDownloads_formatsThousandsAndMillions() {
        assertEquals("500", formatDownloads(500))
        assertEquals("1.5K", formatDownloads(1500))
        assertEquals("2.3M", formatDownloads(2_300_000))
    }

    @Test
    fun formatBytes_formatsReadableUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }
}
