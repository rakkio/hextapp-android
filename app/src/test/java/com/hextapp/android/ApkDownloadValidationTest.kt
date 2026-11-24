package com.hextapp.android

import com.hextapp.android.data.download.ApkDownloadValidation
import com.hextapp.android.data.nexus.ArchiveKind
import com.hextapp.android.data.nexus.NexusEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkDownloadValidationTest {

    @Test
    fun rejectsHtmlAndJsonContentTypes() {
        assertTrue(ApkDownloadValidation.isNonBinaryContentType("text/html; charset=utf-8"))
        assertTrue(ApkDownloadValidation.isNonBinaryContentType("application/json"))
        assertTrue(ApkDownloadValidation.isNonBinaryContentType("text/plain"))
        assertFalse(ApkDownloadValidation.isNonBinaryContentType("application/vnd.android.package-archive"))
        assertFalse(ApkDownloadValidation.isNonBinaryContentType("application/octet-stream"))
        assertFalse(ApkDownloadValidation.isNonBinaryContentType(null))
    }

    @Test
    fun detectsZipAndApkMagicBytes() {
        val validHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val htmlHeader = "<!do".toByteArray()
        assertTrue(ApkDownloadValidation.hasZipMagicBytes(validHeader, 4))
        assertFalse(ApkDownloadValidation.hasZipMagicBytes(htmlHeader, 4))
        assertFalse(ApkDownloadValidation.hasZipMagicBytes(validHeader, 2))
    }

    @Test
    fun detectsKnownHtmlHosterPages() {
        assertTrue(ApkDownloadValidation.isLikelyHtmlPageUrl("https://gofile.io/d/tGXFgC4p"))
        assertTrue(ApkDownloadValidation.isLikelyHtmlPageUrl("https://steamrip.com/game-free-download/"))
        assertFalse(ApkDownloadValidation.isLikelyHtmlPageUrl("https://store1.gofile.io/download/web/abc/app.apk"))
    }

    @Test
    fun rejectsSmall3KbHtmlFileAsInvalidApk() {
        val tempFile = File.createTempFile("test_3kb_html", ".apk")
        try {
            tempFile.writeText("<!doctype html><html><body>Gofile</body></html>")
            assertFalse(ApkDownloadValidation.isValidZipOrApkFile(tempFile))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun acceptsValidSizedZipApkFile() {
        val tempFile = File.createTempFile("test_valid", ".apk")
        try {
            val bytes = ByteArray(64 * 1024)
            bytes[0] = 0x50
            bytes[1] = 0x4B
            bytes[2] = 0x03
            bytes[3] = 0x04
            tempFile.writeBytes(bytes)
            assertTrue(ApkDownloadValidation.isValidZipOrApkFile(tempFile))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun nexusInspectsAndExtractsSplitApks() = runBlocking {
        val zipFile = File.createTempFile("split_bundle", ".apks")
        val outDir = File.createTempFile("nexus_out", "").apply { delete(); mkdirs() }
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zos.putNextEntry(ZipEntry("base.apk"))
                zos.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("split_config.arm64_v8a.apk"))
                zos.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
                zos.closeEntry()
            }
            assertEquals(ArchiveKind.SPLIT_APKS, NexusEngine.inspectArchive(zipFile))
            val extracted = NexusEngine.extractArchive(zipFile, outDir)
            assertEquals(2, extracted.apkFiles.size)
        } finally {
            zipFile.delete()
            outDir.deleteRecursively()
        }
    }

    @Test
    fun nexusDetectsAndExtractsNxs6V2WithXz() = runBlocking {
        val nxsFile = File.createTempFile("test_game", ".nxs6")
        val outDir = File.createTempFile("nexus_nxs_out", "").apply { delete(); mkdirs() }
        try {
            val apkBytes = ByteArray(1024).apply {
                this[0] = 0x50; this[1] = 0x4B; this[2] = 0x03; this[3] = 0x04
            }
            val xzPayload = java.io.ByteArrayOutputStream().use { baos ->
                org.tukaani.xz.XZOutputStream(baos, org.tukaani.xz.LZMA2Options(1)).use { xz ->
                    xz.write(apkBytes)
                }
                baos.toByteArray()
            }
            val nameBytes = "game.apk".toByteArray(Charsets.UTF_8)
            val tocBuf = java.nio.ByteBuffer.allocate(4 + 2 + nameBytes.size + 8 + 8 + 1 + 1 + 8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            tocBuf.putInt(1)
            tocBuf.putShort(nameBytes.size.toShort())
            tocBuf.put(nameBytes)
            tocBuf.putLong(apkBytes.size.toLong())
            tocBuf.putLong(apkBytes.size.toLong())
            tocBuf.put(0) // Raw
            tocBuf.put(0xFF.toByte())
            tocBuf.putLong(0L)
            val tocBytes = tocBuf.array()

            val archiveBuf = java.nio.ByteBuffer.allocate(
                8 + 2 + (6 + tocBytes.size) + (6 + xzPayload.size)
            ).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            archiveBuf.put(byteArrayOf(0x4E, 0x58, 0x53, 0x00, 1, 0, 0, 0))
            archiveBuf.putShort(2)
            archiveBuf.putShort(1) // EXT_TOC
            archiveBuf.putInt(tocBytes.size)
            archiveBuf.put(tocBytes)
            archiveBuf.putShort(2) // EXT_PAYLOAD
            archiveBuf.putInt(xzPayload.size)
            archiveBuf.put(xzPayload)
            nxsFile.writeBytes(archiveBuf.array())

            assertEquals(ArchiveKind.NXS6_ARCHIVE, NexusEngine.inspectArchive(nxsFile))
            val extracted = NexusEngine.extractNxs6(nxsFile, outDir)
            assertEquals(1, extracted.apkFiles.size)
            assertEquals("game.apk", extracted.apkFiles.first().name)
            assertEquals(apkBytes.size.toLong(), extracted.apkFiles.first().length())
        } finally {
            nxsFile.delete()
            outDir.deleteRecursively()
        }
    }

    @Test
    fun nexusPackAndExtractApkWithSpecialCharsInPng() = runBlocking {
        val rawApk = File.createTempFile("infinite_flight_test", ".apk")
        val nx6rFile = File.createTempFile("infinite_flight_test", ".nx6r")
        val outDir = File.createTempFile("nexus_special_out", "").apply { delete(); mkdirs() }
        val specialEntry = "assets/Content/UI/background-1024*768.png"
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5)
        try {
            ZipOutputStream(FileOutputStream(rawApk)).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write("<manifest package=\"com.test.flight\"/>".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry(specialEntry))
                zos.write(pngBytes)
                zos.closeEntry()
            }

            NexusEngine.packNx6r(rawApk, nx6rFile, preset = 1)
            val extracted = NexusEngine.extractNxs6(nx6rFile, outDir, signingContext = null)
            assertEquals(1, extracted.apkFiles.size)
            val rebuiltApk = extracted.apkFiles.first()
            java.util.zip.ZipFile(rebuiltApk).use { zf ->
                val entry = zf.getEntry(specialEntry)
                assertTrue("Debe existir $specialEntry en el APK reconstruido", entry != null)
                val actualBytes = zf.getInputStream(entry).use { it.readBytes() }
                assertTrue(pngBytes.contentEquals(actualBytes))
            }
        } finally {
            rawApk.delete()
            nx6rFile.delete()
            outDir.deleteRecursively()
        }
    }
}
