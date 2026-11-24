package com.hextapp.android.data.nexus

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import com.android.apksig.ApkSigner
import com.hextapp.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ArchiveKind {
    SINGLE_APK,
    ZIP_WITH_SINGLE_APK,
    SPLIT_APKS,
    NXS6_ARCHIVE,
    GENERIC_ZIP
}

data class ExtractedPackage(
    val kind: ArchiveKind,
    val apkFiles: List<File>,
    val obbFiles: List<File>,
    val outputDir: File
)

private data class Nxs6Entry(
    val name: String,
    val preSize: Long,
    val offset: Long,
    val chunkId: Int = 0xFF,
    val preprocessor: Int = 0
)

private data class Nx6rChunkMeta(
    val chunkId: Int,
    val payloadOffset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long
)

private data class Nx6rApkEntryMeta(
    val entryName: String,
    val method: Int,
    val crc32: Long,
    val uncompressedSize: Long
)

private data class Nx6rApkManifest(
    val apkName: String,
    val entries: List<Nx6rApkEntryMeta>
)

object NexusEngine {

    const val ACTION_INSTALL_RESULT = "com.hextapp.android.NEXUS_INSTALL_RESULT"
    private const val EXT_TOC = 0x0001
    private const val EXT_PAYLOAD = 0x0002
    private const val EXT_APK_MANIFEST = 0x0060
    private const val EXT_SHA256 = 0x0061
    private const val EXT_CHUNKS = 0x0062

    fun isNxs6File(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 8) return false
        return runCatching {
            val header = ByteArray(5)
            FileInputStream(file).use { it.read(header) }
            hasNxsMagicBytes(header, header.size)
        }.getOrDefault(false)
    }

    fun hasNxsMagicBytes(header: ByteArray, length: Int): Boolean {
        if (length >= 4 &&
            header[0] == 0x4E.toByte() && // N
            header[1] == 0x58.toByte() && // X
            header[2] == 0x53.toByte() && // S
            header[3] == 0x00.toByte()    // \0 (NXS v2 / NX6R)
        ) {
            return true
        }
        if (length >= 5 &&
            header[0] == 0x4E.toByte() && // N
            header[1] == 0x58.toByte() && // X
            header[2] == 0x53.toByte() && // S
            header[3] == 0x36.toByte() && // 6
            header[4] == 0x0A.toByte()    // \n (NXS6 v1)
        ) {
            return true
        }
        return false
    }

    fun inspectArchive(file: File): ArchiveKind {
        if (!file.exists() || !file.isFile) return ArchiveKind.GENERIC_ZIP
        if (isNxs6File(file)) return ArchiveKind.NXS6_ARCHIVE
        return runCatching {
            ZipFile(file).use { zip ->
                var hasRootManifest = false
                var nestedApkCount = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name.startsWith("__MACOSX/", ignoreCase = true)) continue
                    if (name.equals("AndroidManifest.xml", ignoreCase = true)) {
                        hasRootManifest = true
                    } else if (name.endsWith(".apk", ignoreCase = true)) {
                        nestedApkCount++
                    }
                }
                when {
                    nestedApkCount > 1 -> ArchiveKind.SPLIT_APKS
                    nestedApkCount == 1 -> ArchiveKind.ZIP_WITH_SINGLE_APK
                    hasRootManifest -> ArchiveKind.SINGLE_APK
                    else -> ArchiveKind.GENERIC_ZIP
                }
            }
        }.getOrDefault(ArchiveKind.SINGLE_APK)
    }

    suspend fun extractArchive(
        archiveFile: File,
        targetDir: File,
        signingContext: Context? = null,
        onProgress: (percent: Int, currentEntry: String) -> Unit = { _, _ -> }
    ): ExtractedPackage = withContext(Dispatchers.IO) {
        if (isNxs6File(archiveFile)) {
            return@withContext extractNxs6(archiveFile, targetDir, signingContext, onProgress)
        }

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        val kind = inspectArchive(archiveFile)
        val canonicalTarget = targetDir.canonicalPath
        val apkFiles = mutableListOf<File>()
        val obbFiles = mutableListOf<File>()

        val totalEntries = runCatching {
            ZipFile(archiveFile).use { zip ->
                zip.entries().asSequence().count { !it.isDirectory }
            }
        }.getOrDefault(1).coerceAtLeast(1)

        var processed = 0
        val buffer = ByteArray(32 * 1024)

        ZipInputStream(FileInputStream(archiveFile)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory || entry.name.startsWith("__MACOSX/", ignoreCase = true)) {
                    zis.closeEntry()
                    continue
                }

                val isVirtualApkEntry = entry.name.startsWith("__nx6r_apk__/")
                val outFile = if (isVirtualApkEntry) {
                    safeVirtualStagingFile(targetDir, entry.name)
                } else {
                    safeResolve(targetDir, canonicalTarget, entry.name)
                }
                outFile.parentFile?.mkdirs()

                FileOutputStream(outFile).use { fos ->
                    while (true) {
                        val read = zis.read(buffer)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                    }
                }
                zis.closeEntry()

                when {
                    outFile.name.endsWith(".apk", ignoreCase = true) -> apkFiles.add(outFile)
                    outFile.name.endsWith(".obb", ignoreCase = true) -> obbFiles.add(outFile)
                }

                processed++
                val percent = ((processed * 100) / totalEntries).coerceIn(0, 100)
                onProgress(percent, outFile.name)
            }
        }

        ExtractedPackage(
            kind = kind,
            apkFiles = apkFiles,
            obbFiles = obbFiles,
            outputDir = targetDir
        )
    }

    suspend fun extractNxs6(
        archiveFile: File,
        targetDir: File,
        signingContext: Context? = null,
        onProgress: (percent: Int, currentEntry: String) -> Unit = { _, _ -> }
    ): ExtractedPackage = withContext(Dispatchers.IO) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalPath

        val apkFiles = Collections.synchronizedList(mutableListOf<File>())
        val obbFiles = Collections.synchronizedList(mutableListOf<File>())
        val nestedZipFiles = Collections.synchronizedList(mutableListOf<File>())
        var apkManifests: List<Nx6rApkManifest> = emptyList()
        var sha256Map: Map<String, ByteArray> = emptyMap()
        var chunkMetas: List<Nx6rChunkMeta> = emptyList()
        var payloadStartOffset = -1L

        val entries: List<Nxs6Entry>
        var legacyPayloadStream: InputStream? = null
        var legacyBis: BufferedInputStream? = null

        val fis = FileInputStream(archiveFile)
        val bis = BufferedInputStream(fis, 64 * 1024)
        var keepStreamOpenForLegacy = false
        try {
            val dis = DataInputStream(bis)
            val magic = ByteArray(5)
            dis.readFully(magic, 0, 4)

            if (magic[0] == 0x4E.toByte() && magic[1] == 0x58.toByte() &&
                magic[2] == 0x53.toByte() && magic[3] == 0x00.toByte()
            ) {
                val headerRest = ByteArray(4)
                dis.readFully(headerRest)
                val version = headerRest[0].toInt() and 0xFF
                require(version == 1) { "Versión NXS6 v2 no soportada: $version" }

                val nExtensions = readU16LE(dis)
                var parsedToc: List<Nxs6Entry>? = null
                var foundPayload = false
                var pos = 10L

                for (i in 0 until nExtensions) {
                    val extId = readU16LE(dis)
                    val extSize = readU32LE(dis)
                    pos += 6L
                    when (extId) {
                        EXT_TOC -> {
                            val tocBytes = ByteArray(extSize.toInt())
                            dis.readFully(tocBytes)
                            parsedToc = parseV2Toc(tocBytes)
                            pos += extSize
                        }
                        EXT_APK_MANIFEST -> {
                            val manifestBytes = ByteArray(extSize.toInt())
                            dis.readFully(manifestBytes)
                            apkManifests = parseApkManifests(manifestBytes)
                            pos += extSize
                        }
                        EXT_SHA256 -> {
                            val shaBytes = ByteArray(extSize.toInt())
                            dis.readFully(shaBytes)
                            sha256Map = parseSha256Map(shaBytes)
                            pos += extSize
                        }
                        EXT_CHUNKS -> {
                            val chunkBytes = ByteArray(extSize.toInt())
                            dis.readFully(chunkBytes)
                            chunkMetas = parseChunkMetas(chunkBytes)
                            pos += extSize
                        }
                        EXT_PAYLOAD -> {
                            payloadStartOffset = pos
                            foundPayload = true
                            break
                        }
                        else -> {
                            skipExact(dis, extSize)
                            pos += extSize
                        }
                    }
                }
                require(foundPayload) { "Archivo NXS6 sin EXT_PAYLOAD" }
                entries = parsedToc ?: error("Archivo NXS6 sin EXT_TOC")
                if (chunkMetas.isEmpty()) {
                    legacyPayloadStream = XZInputStream(dis)
                    legacyBis = bis
                    keepStreamOpenForLegacy = true
                }
            } else {
                dis.readFully(magic, 4, 1)
                require(
                    magic[0] == 0x4E.toByte() && magic[1] == 0x58.toByte() &&
                        magic[2] == 0x53.toByte() && magic[3] == 0x36.toByte() &&
                        magic[4] == 0x0A.toByte()
                ) { "Cabecera NXS6 inválida" }

                val version = dis.readUnsignedByte()
                require(version == 1) { "Versión NXS6 v1 no soportada: $version" }
                val nFiles = readU32LE(dis).toInt()
                val v1Entries = ArrayList<Nxs6Entry>(nFiles)
                for (i in 0 until nFiles) {
                    val nameLen = readU16LE(dis)
                    val nameBytes = ByteArray(nameLen)
                    dis.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)
                    readU64LE(dis) // orig_size
                    val preSize = readU64LE(dis)
                    dis.readUnsignedByte() // preproc
                    val offset = readU64LE(dis)
                    v1Entries.add(Nxs6Entry(name = name, preSize = preSize, offset = offset))
                }
                entries = v1Entries
                legacyPayloadStream = XZInputStream(dis)
                legacyBis = bis
                keepStreamOpenForLegacy = true
            }
        } finally {
            if (!keepStreamOpenForLegacy) {
                bis.close()
            }
        }

        val totalBytes = entries.sumOf { it.preSize }.coerceAtLeast(1L)
        val processedBytes = AtomicLong(0L)

        fun arm64BcjDecodeBlock(buf: ByteArray, alignedLen: Int, baseOffset: Long) {
            var i = 0
            while (i + 4 <= alignedLen) {
                val b0 = buf[i].toInt() and 0xFF
                val b1 = buf[i + 1].toInt() and 0xFF
                val b2 = buf[i + 2].toInt() and 0xFF
                val b3 = buf[i + 3].toInt() and 0xFF
                if ((b3 and 0xFC) == 0x94) {
                    val insn = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    val abs26 = insn and 0x03FFFFFF
                    val pcWord = ((baseOffset + i) ushr 2).toInt()
                    val imm26 = (abs26 - pcWord) and 0x03FFFFFF
                    val orig = (0x94 shl 24) or imm26
                    buf[i] = (orig and 0xFF).toByte()
                    buf[i + 1] = ((orig ushr 8) and 0xFF).toByte()
                    buf[i + 2] = ((orig ushr 16) and 0xFF).toByte()
                    buf[i + 3] = ((orig ushr 24) and 0xFF).toByte()
                }
                i += 4
            }
        }

        fun extractStreamSubset(xz: InputStream, subset: List<Nxs6Entry>) {
            var streamPos = 0L
            val buffer = ByteArray(64 * 1024)
            var lastOffset = -1L
            var lastPreSize = -1L
            var lastOutFile: File? = null

            for (entry in subset.sortedBy { it.offset }) {
                val isVirtualApkEntry = entry.name.startsWith("__nx6r_apk__/")
                val outFile = if (isVirtualApkEntry) {
                    safeVirtualStagingFile(targetDir, entry.name)
                } else {
                    safeResolve(targetDir, canonicalTarget, entry.name)
                }
                outFile.parentFile?.mkdirs()

                if (entry.offset == lastOffset && entry.preSize == lastPreSize && lastOutFile != null && lastOutFile.exists()) {
                    if (outFile.canonicalPath != lastOutFile.canonicalPath) {
                        lastOutFile.copyTo(outFile, overwrite = true)
                    }
                    val done = processedBytes.addAndGet(entry.preSize)
                    val pct = ((done * 100) / totalBytes).toInt().coerceIn(0, 100)
                    onProgress(pct, outFile.name)
                } else {
                    if (entry.offset > streamPos) {
                        skipExact(xz, entry.offset - streamPos)
                        streamPos = entry.offset
                    }

                    val digest = if (sha256Map.containsKey(entry.name)) {
                        MessageDigest.getInstance("SHA-256")
                    } else null

                    var remaining = entry.preSize
                    var carryLen = 0
                    var filePos = 0L
                    FileOutputStream(outFile).use { fos ->
                        while (remaining > 0) {
                            val toRead = minOf(remaining, (buffer.size - carryLen).toLong()).toInt()
                            val read = xz.read(buffer, carryLen, toRead)
                            if (read == -1) throw EOFException("Fin inesperado del flujo NXS6")
                            remaining -= read
                            streamPos += read
                            val totalBuf = carryLen + read
                            if (entry.preprocessor == 1) {
                                val aligned = (totalBuf / 4) * 4
                                if (aligned > 0) {
                                    arm64BcjDecodeBlock(buffer, aligned, filePos)
                                    fos.write(buffer, 0, aligned)
                                    digest?.update(buffer, 0, aligned)
                                    filePos += aligned
                                }
                                carryLen = totalBuf - aligned
                                if (carryLen > 0) {
                                    System.arraycopy(buffer, aligned, buffer, 0, carryLen)
                                }
                            } else {
                                fos.write(buffer, 0, totalBuf)
                                digest?.update(buffer, 0, totalBuf)
                                filePos += totalBuf
                                carryLen = 0
                            }
                            val done = processedBytes.addAndGet(read.toLong())
                            val pct = ((done * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(pct, outFile.name)
                        }
                        if (carryLen > 0) {
                            fos.write(buffer, 0, carryLen)
                            digest?.update(buffer, 0, carryLen)
                        }
                    }

                    if (digest != null) {
                        val expected = sha256Map[entry.name]!!
                        val actual = digest.digest()
                        if (!expected.contentEquals(actual)) {
                            outFile.delete()
                            throw SecurityException("SHA-256 inválido en ${entry.name}")
                        }
                    }
                    lastOffset = entry.offset
                    lastPreSize = entry.preSize
                    lastOutFile = outFile
                }

                if (!isVirtualApkEntry) {
                    when {
                        outFile.name.endsWith(".apk", ignoreCase = true) -> apkFiles.add(outFile)
                        outFile.name.endsWith(".obb", ignoreCase = true) -> obbFiles.add(outFile)
                        outFile.name.endsWith(".apks", ignoreCase = true) ||
                            outFile.name.endsWith(".xapk", ignoreCase = true) ||
                            outFile.name.endsWith(".zip", ignoreCase = true) -> nestedZipFiles.add(outFile)
                    }
                }
            }
        }

        if (chunkMetas.isNotEmpty() && payloadStartOffset > 0L) {
            val entriesByChunk = entries.groupBy { it.chunkId }
            val maxWorkers = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val semaphore = Semaphore(maxWorkers)
            coroutineScope {
                chunkMetas.map { cm ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val subset = entriesByChunk[cm.chunkId].orEmpty()
                            if (subset.isEmpty()) return@withPermit
                            RandomAccessFile(archiveFile, "r").use { raf ->
                                raf.seek(payloadStartOffset + cm.payloadOffset)
                                val bounded = BoundedInputStream(raf, cm.compressedSize)
                                XZInputStream(BufferedInputStream(bounded, 64 * 1024)).use { xz ->
                                    extractStreamSubset(xz, subset)
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        } else {
            legacyPayloadStream?.use { xz ->
                extractStreamSubset(xz, entries)
            }
            legacyBis?.close()
        }

        // Reconstrucción NX6R APK-aware / OBB-aware
        if (apkManifests.isNotEmpty()) {
            val virtualRoot = File(targetDir, "__nx6r_apk__")
            for (manifest in apkManifests) {
                val isObbZip = manifest.apkName.endsWith(".obb", ignoreCase = true)
                val reconstructed = reconstructAndSignApk(
                    manifest = manifest,
                    targetDir = targetDir,
                    outputDir = targetDir,
                    signingContext = if (isObbZip) null else signingContext
                )
                if (isObbZip) {
                    obbFiles.add(reconstructed)
                } else {
                    apkFiles.add(reconstructed)
                }
            }
            virtualRoot.deleteRecursively()
        }

        if (apkFiles.isEmpty() && nestedZipFiles.isNotEmpty()) {
            for (nested in nestedZipFiles) {
                val subDir = File(targetDir, nested.nameWithoutExtension)
                val subExtracted = extractArchive(nested, subDir, signingContext, onProgress)
                apkFiles.addAll(subExtracted.apkFiles)
                obbFiles.addAll(subExtracted.obbFiles)
            }
        }

        val resolvedKind = when {
            apkFiles.size > 1 -> ArchiveKind.SPLIT_APKS
            apkFiles.size == 1 -> ArchiveKind.ZIP_WITH_SINGLE_APK
            else -> ArchiveKind.GENERIC_ZIP
        }

        ExtractedPackage(
            kind = resolvedKind,
            apkFiles = apkFiles,
            obbFiles = obbFiles,
            outputDir = targetDir
        )
    }

    private fun reconstructAndSignApk(
        manifest: Nx6rApkManifest,
        targetDir: File,
        outputDir: File,
        signingContext: Context?
    ): File {
        val outApk = safeResolve(outputDir, outputDir.canonicalPath, manifest.apkName)
        outApk.parentFile?.mkdirs()
        val unsignedApk = File(outApk.parentFile, "${outApk.name}.unsigned")
        if (unsignedApk.exists()) unsignedApk.delete()

        val buffer = ByteArray(64 * 1024)
        val seenEntries = HashSet<String>(manifest.entries.size)
        val countingOut = CountingOutputStream(BufferedOutputStream(FileOutputStream(unsignedApk), 64 * 1024))
        ZipOutputStream(countingOut).use { zos ->
            for (item in manifest.entries) {
                if (!seenEntries.add(item.entryName)) continue
                val src = safeVirtualStagingFile(targetDir, "__nx6r_apk__/${manifest.apkName}/${item.entryName}")
                if (!src.exists()) continue

                val entry = ZipEntry(item.entryName)
                if (item.method == ZipEntry.STORED) {
                    entry.method = ZipEntry.STORED
                    entry.size = src.length()
                    entry.crc = computeCrc32(src, buffer)
                    // Android 11+ exige alineación de 4 bytes para entradas STORED (resources.arsc, .so)
                    zos.flush()
                    val nameBytesLen = item.entryName.toByteArray(Charsets.UTF_8).size
                    val dataOffset = countingOut.bytesWritten + 30L + nameBytesLen
                    val alignBias = if (item.entryName.endsWith(".so", ignoreCase = true)) 4096L else 4L
                    val rem = (dataOffset % alignBias).toInt()
                    val pad = if (rem == 0) 0 else (alignBias.toInt() - rem)
                    if (pad > 0) {
                        // Bloque ZIP extra válido (ID 0xD935 de zipalign, mínimo 4 bytes)
                        val extraLen = if (pad < 4) pad + alignBias.toInt() else pad
                        val dataLen = extraLen - 4
                        val extra = ByteArray(extraLen)
                        extra[0] = 0x35.toByte()
                        extra[1] = 0xD9.toByte()
                        extra[2] = (dataLen and 0xFF).toByte()
                        extra[3] = ((dataLen ushr 8) and 0xFF).toByte()
                        entry.extra = extra
                    }
                } else {
                    entry.method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                FileInputStream(src).use { fis ->
                    while (true) {
                        val read = fis.read(buffer)
                        if (read == -1) break
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
            }
        }

        if (signingContext != null) {
            signApkFile(signingContext, unsignedApk, outApk)
            unsignedApk.delete()
        } else {
            if (outApk.exists()) outApk.delete()
            unsignedApk.renameTo(outApk)
        }
        return outApk
    }

    private fun signApkFile(context: Context, inputApk: File, outputApk: File) {
        val ks = KeyStore.getInstance("PKCS12")
        context.resources.openRawResource(R.raw.nexus_sign).use { stream ->
            ks.load(stream, "nexus6r".toCharArray())
        }
        val alias = ks.aliases().nextElement()
        val privateKey = ks.getKey(alias, "nexus6r".toCharArray()) as PrivateKey
        val cert = ks.getCertificate(alias) as X509Certificate

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "nexus",
            privateKey,
            listOf(cert)
        ).build()

        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()
    }

    private fun computeCrc32(file: File, buffer: ByteArray): Long {
        val crc = CRC32()
        FileInputStream(file).use { fis ->
            while (true) {
                val read = fis.read(buffer)
                if (read == -1) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    private fun parseV2Toc(bytes: ByteArray): List<Nxs6Entry> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val nEntries = bb.int.toLong() and 0xFFFFFFFFL
        val out = ArrayList<Nxs6Entry>(nEntries.toInt())
        for (i in 0 until nEntries.toInt()) {
            val nameLen = bb.short.toInt() and 0xFFFF
            val nameBytes = ByteArray(nameLen)
            bb.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)
            bb.long // original_size
            val preSize = bb.long
            val preproc = bb.get().toInt() and 0xFF
            val chunkId = bb.get().toInt() and 0xFF
            val chunkOffset = bb.long
            out.add(Nxs6Entry(name = name, preSize = preSize, offset = chunkOffset, chunkId = chunkId, preprocessor = preproc))
        }
        return out
    }

    private fun parseChunkMetas(bytes: ByteArray): List<Nx6rChunkMeta> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val nChunks = bb.int.toLong() and 0xFFFFFFFFL
        val out = ArrayList<Nx6rChunkMeta>(nChunks.toInt())
        for (i in 0 until nChunks.toInt()) {
            val cid = bb.get().toInt() and 0xFF
            val payloadOffset = bb.long
            val compressedSize = bb.long
            val uncompressedSize = bb.long
            out.add(Nx6rChunkMeta(cid, payloadOffset, compressedSize, uncompressedSize))
        }
        return out
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len
        }
    }

    private class BoundedInputStream(
        private val raf: RandomAccessFile,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0L) return -1
            val b = raf.read()
            if (b != -1) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = raf.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }
    }

    private fun parseApkManifests(bytes: ByteArray): List<Nx6rApkManifest> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val nApks = bb.int.toLong() and 0xFFFFFFFFL
        val out = ArrayList<Nx6rApkManifest>(nApks.toInt())
        for (i in 0 until nApks.toInt()) {
            val apkNameLen = bb.short.toInt() and 0xFFFF
            val nItems = bb.int.toLong() and 0xFFFFFFFFL
            val apkNameBytes = ByteArray(apkNameLen)
            bb.get(apkNameBytes)
            val apkName = String(apkNameBytes, Charsets.UTF_8)

            val entries = ArrayList<Nx6rApkEntryMeta>(nItems.toInt())
            for (j in 0 until nItems.toInt()) {
                val entryNameLen = bb.short.toInt() and 0xFFFF
                val entryNameBytes = ByteArray(entryNameLen)
                bb.get(entryNameBytes)
                val entryName = String(entryNameBytes, Charsets.UTF_8)
                val method = bb.short.toInt() and 0xFFFF
                val crc32 = bb.int.toLong() and 0xFFFFFFFFL
                val uncompSize = bb.long
                entries.add(Nx6rApkEntryMeta(entryName, method, crc32, uncompSize))
            }
            out.add(Nx6rApkManifest(apkName, entries))
        }
        return out
    }

    private fun parseSha256Map(bytes: ByteArray): Map<String, ByteArray> {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val nHashes = bb.int.toLong() and 0xFFFFFFFFL
        val map = HashMap<String, ByteArray>(nHashes.toInt())
        for (i in 0 until nHashes.toInt()) {
            val pathLen = bb.short.toInt() and 0xFFFF
            val pathBytes = ByteArray(pathLen)
            bb.get(pathBytes)
            val digest = ByteArray(32)
            bb.get(digest)
            map[String(pathBytes, Charsets.UTF_8)] = digest
        }
        return map
    }

    private fun safeVirtualStagingFile(targetDir: File, virtualEntryName: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(virtualEntryName.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        val vRoot = File(targetDir, "__nx6r_apk__")
        vRoot.mkdirs()
        return File(vRoot, "$hex.bin")
    }

    private fun safeResolve(targetDir: File, canonicalTarget: String, entryName: String): File {
        val sanitized = entryName.replace('\\', '/')
            .trimStart('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { seg ->
                if (seg == "." || seg == "..") seg
                else seg.replace(Regex("[*?:\"<>|]"), "_")
            }
        val outFile = File(targetDir, sanitized)
        if (!outFile.canonicalPath.startsWith(canonicalTarget + File.separator)) {
            throw SecurityException("Entrada inválida en archivo: $entryName")
        }
        return outFile
    }

    private fun readU16LE(dis: DataInputStream): Int {
        val b0 = dis.readUnsignedByte()
        val b1 = dis.readUnsignedByte()
        return b0 or (b1 shl 8)
    }

    private fun readU32LE(dis: DataInputStream): Long {
        val b0 = dis.readUnsignedByte().toLong()
        val b1 = dis.readUnsignedByte().toLong()
        val b2 = dis.readUnsignedByte().toLong()
        val b3 = dis.readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readU64LE(dis: DataInputStream): Long {
        val buf = ByteArray(8)
        dis.readFully(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun skipExact(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val skipBuf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, skipBuf.size.toLong()).toInt()
            val read = input.read(skipBuf, 0, toRead)
            if (read == -1) throw EOFException("EOF inesperado al saltar $remaining bytes")
            remaining -= read
        }
    }

    suspend fun installSplitApks(
        context: Context,
        apkFiles: List<File>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(apkFiles.isNotEmpty()) { "No se encontraron archivos APK para instalar" }

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setSize(apkFiles.sumOf { it.length() })
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            try {
                val buffer = ByteArray(64 * 1024)
                for ((index, apk) in apkFiles.withIndex()) {
                    val splitName = "${index}_${apk.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                    session.openWrite(splitName, 0, apk.length()).use { out ->
                        FileInputStream(apk).use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                            }
                        }
                        session.fsync(out)
                    }
                }

                val intent = Intent(context, NexusInstallReceiver::class.java).apply {
                    action = ACTION_INSTALL_RESULT
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    flags
                )
                session.commit(pendingIntent.intentSender)
            } catch (e: Exception) {
                session.abandon()
                throw e
            } finally {
                session.close()
            }
        }
    }

    fun installObbFiles(obbFiles: List<File>): Int {
        if (obbFiles.isEmpty()) return 0
        val extRoot = Environment.getExternalStorageDirectory()
        val obbBase = File(extRoot, "Android/obb")
        var copied = 0
        val obbRegex = Regex("""^(?:main|patch)\.\d+\.([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)\.obb$""", RegexOption.IGNORE_CASE)
        for (obb in obbFiles) {
            if (!obb.exists() || !obb.isFile) continue
            val match = obbRegex.matchEntire(obb.name)
            val pkgDir = if (match != null) {
                File(obbBase, match.groupValues[1])
            } else {
                val parentPkg = obb.parentFile?.name?.takeIf { it.contains('.') } ?: continue
                File(obbBase, parentPkg)
            }
            runCatching {
                pkgDir.mkdirs()
                val dest = File(pkgDir, obb.name)
                if (dest.exists()) dest.delete()
                obb.copyTo(dest, overwrite = true)
                copied++
            }
        }
        return copied
    }

    private val UNCOMPRESSED_EXTS = setOf(
        ".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp3", ".ogg", ".aac",
        ".mp4", ".m4a", ".wav", ".so", ".arsc"
    )

    suspend fun packNx6r(
        sourceFile: File,
        outputFile: File,
        preset: Int = 4,
        onProgress: (percent: Int, currentEntry: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        require(sourceFile.exists()) { "No existe el archivo de origen: ${sourceFile.name}" }
        outputFile.parentFile?.mkdirs()

        val tmpDir = File(outputFile.parentFile, ".nx6r_pack_${System.currentTimeMillis()}")
        tmpDir.mkdirs()
        val tmpXz = File(tmpDir, "payload.xz")

        try {
            val inputFiles = mutableListOf<Pair<File, String>>()
            val nameLower = sourceFile.name.lowercase()
            if (sourceFile.isFile && (nameLower.endsWith(".xapk") || nameLower.endsWith(".apks"))) {
                val bundleDir = File(tmpDir, "bundle")
                bundleDir.mkdirs()
                ZipFile(sourceFile).use { zf ->
                    val entries = zf.entries()
                    val buf = ByteArray(64 * 1024)
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.isDirectory) continue
                        val out = safeResolve(bundleDir, bundleDir.canonicalPath, e.name)
                        out.parentFile?.mkdirs()
                        zf.getInputStream(e).use { ins ->
                            FileOutputStream(out).use { fos ->
                                while (true) {
                                    val r = ins.read(buf)
                                    if (r == -1) break
                                    fos.write(buf, 0, r)
                                }
                            }
                        }
                        inputFiles.add(out to e.name.replace('\\', '/'))
                    }
                }
            } else if (sourceFile.isFile) {
                inputFiles.add(sourceFile to sourceFile.name)
            } else {
                sourceFile.walkTopDown().filter { it.isFile }.forEach { f ->
                    inputFiles.add(f to f.relativeTo(sourceFile).path.replace('\\', '/'))
                }
            }

            val tocEntries = mutableListOf<Nxs6Entry>()
            val shaList = mutableListOf<Pair<String, ByteArray>>()
            val apkManifests = mutableListOf<Nx6rApkManifest>()
            var streamOffset = 0L
            val totalFiles = inputFiles.size.coerceAtLeast(1)

            XZOutputStream(BufferedOutputStream(FileOutputStream(tmpXz), 64 * 1024), LZMA2Options(preset.coerceIn(0, 6))).use { xzOut ->
                val buf = ByteArray(64 * 1024)
                inputFiles.forEachIndexed { idx, (file, relName) ->
                    val pct = (((idx + 1) * 90) / totalFiles).coerceIn(1, 90)
                    onProgress(pct, relName)

                    val isApk = file.name.endsWith(".apk", ignoreCase = true) &&
                        runCatching { ZipFile(file).use { true } }.getOrDefault(false)

                    if (isApk) {
                        val apkItems = mutableListOf<Nx6rApkEntryMeta>()
                        val seenApkEntries = HashSet<String>()
                        ZipFile(file).use { zf ->
                            val entries = zf.entries()
                            while (entries.hasMoreElements()) {
                                val info = entries.nextElement()
                                if (info.isDirectory) continue
                                if (!seenApkEntries.add(info.name)) continue
                                val upper = info.name.uppercase()
                                if (upper.startsWith("META-INF/") && (
                                        upper.endsWith(".SF") || upper.endsWith(".RSA") ||
                                            upper.endsWith(".DSA") || upper.endsWith(".EC") ||
                                            upper == "META-INF/MANIFEST.MF"
                                        )
                                ) {
                                    continue
                                }
                                val data = zf.getInputStream(info).use { it.readBytes() }
                                val digest = MessageDigest.getInstance("SHA-256").digest(data)
                                val vName = "__nx6r_apk__/$relName/${info.name}"
                                shaList.add(vName to digest)

                                val ext = ".${info.name.substringAfterLast('.', "").lowercase()}"
                                val method = if (info.method == ZipEntry.STORED || UNCOMPRESSED_EXTS.contains(ext)) {
                                    ZipEntry.STORED
                                } else {
                                    ZipEntry.DEFLATED
                                }
                                apkItems.add(Nx6rApkEntryMeta(info.name, method, info.crc, data.size.toLong()))
                                tocEntries.add(Nxs6Entry(vName, data.size.toLong(), streamOffset, 0xFF))
                                xzOut.write(data)
                                streamOffset += data.size.toLong()
                            }
                        }
                        apkManifests.add(Nx6rApkManifest(relName, apkItems))
                    } else {
                        val md = MessageDigest.getInstance("SHA-256")
                        var fSize = 0L
                        FileInputStream(file).use { fis ->
                            while (true) {
                                val r = fis.read(buf)
                                if (r == -1) break
                                md.update(buf, 0, r)
                                xzOut.write(buf, 0, r)
                                fSize += r
                            }
                        }
                        shaList.add(relName to md.digest())
                        tocEntries.add(Nxs6Entry(relName, fSize, streamOffset, 0xFF))
                        streamOffset += fSize
                    }
                }
            }

            onProgress(95, "Escribiendo cabecera NX6R...")
            val tocBytes = serializeV2Toc(tocEntries)
            val shaBytes = serializeSha256(shaList)
            val exts = mutableListOf<Pair<Int, ByteArray>>(
                EXT_TOC to tocBytes,
                EXT_SHA256 to shaBytes
            )
            if (apkManifests.isNotEmpty()) {
                exts.add(EXT_APK_MANIFEST to serializeApkManifests(apkManifests))
            }

            val nExtensions = exts.size + 1
            val xzSize = tmpXz.length()
            BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024).use { out ->
                out.write(byteArrayOf(0x4E, 0x58, 0x53, 0x00))
                out.write(1) // version
                out.write(0) // flags
                out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putShort(0).putShort(nExtensions.toShort()).array())
                for ((eid, epayload) in exts) {
                    out.write(ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putShort(eid.toShort()).putInt(epayload.size).array())
                    out.write(epayload)
                }
                out.write(ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putShort(EXT_PAYLOAD.toShort()).putInt(xzSize.toInt()).array())
                FileInputStream(tmpXz).use { xzin ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = xzin.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                    }
                }
            }
            onProgress(100, outputFile.name)
            outputFile
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun serializeV2Toc(entries: List<Nxs6Entry>): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(entries.size).array())
        for (e in entries) {
            val nb = e.name.toByteArray(Charsets.UTF_8)
            val bb = ByteBuffer.allocate(2 + nb.size + 26).order(ByteOrder.LITTLE_ENDIAN)
            bb.putShort(nb.size.toShort())
            bb.put(nb)
            bb.putLong(e.preSize)
            bb.putLong(e.preSize)
            bb.put(0)
            bb.put(e.chunkId.toByte())
            bb.putLong(e.offset)
            baos.write(bb.array())
        }
        return baos.toByteArray()
    }

    private fun serializeSha256(list: List<Pair<String, ByteArray>>): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(list.size).array())
        for ((rel, digest) in list) {
            val rb = rel.toByteArray(Charsets.UTF_8)
            baos.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(rb.size.toShort()).array())
            baos.write(rb)
            baos.write(digest)
        }
        return baos.toByteArray()
    }

    private fun serializeApkManifests(manifests: List<Nx6rApkManifest>): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(manifests.size).array())
        for (m in manifests) {
            val ab = m.apkName.toByteArray(Charsets.UTF_8)
            baos.write(ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putShort(ab.size.toShort()).putInt(m.entries.size).array())
            baos.write(ab)
            for (item in m.entries) {
                val eb = item.entryName.toByteArray(Charsets.UTF_8)
                val bb = ByteBuffer.allocate(2 + eb.size + 14).order(ByteOrder.LITTLE_ENDIAN)
                bb.putShort(eb.size.toShort())
                bb.put(eb)
                bb.putShort(item.method.toShort())
                bb.putInt(item.crc32.toInt())
                bb.putLong(item.uncompressedSize)
                baos.write(bb.array())
            }
        }
        return baos.toByteArray()
    }
}
