package com.hextapp.android

import com.hextapp.android.data.update.AppUpdateComparator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateComparatorTest {
    @Test
    fun `detects newer patch minor and major versions`() {
        assertTrue(AppUpdateComparator.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(AppUpdateComparator.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(AppUpdateComparator.isNewerVersion("v2.0.0", "1.9.9"))
    }

    @Test
    fun `returns false for same or older versions`() {
        assertFalse(AppUpdateComparator.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateComparator.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(AppUpdateComparator.isNewerVersion("v0.9.5", "1.0.0"))
    }
}
