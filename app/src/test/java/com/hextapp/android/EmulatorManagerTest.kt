package com.hextapp.android

import com.hextapp.android.data.emulators.EmulatorManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EmulatorManagerTest {
    @Test
    fun pickRomByExtensionRespectsPriorityOrder() {
        val files = listOf(
            File("/tmp/game.bin"),
            File("/tmp/game.cue"),
            File("/tmp/readme.txt")
        )
        val picked = EmulatorManager.pickRomByExtension(files, listOf(".chd", ".cue", ".bin"))
        assertEquals("game.cue", picked?.name)
    }
}
