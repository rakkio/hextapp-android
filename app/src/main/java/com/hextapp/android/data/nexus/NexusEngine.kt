package com.hextapp.android.data.nexus

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.android.apksig.ApkSigner
import com.hextapp.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
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
    val offset: Long
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

                val outFile = safeResolve(targetDir, canonicalTarget, entry.name)
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

        val apkFiles = mutableListOf<File>()
        val obbFiles = mutableListOf<File>()
        val nestedZipFiles = mutableListOf<File>()
        var apkManifests: List<Nx6rApkManifest> = emptyList()
        var sha256Map: Map<String, ByteArray> = emptyMap()

        BufferedInputStream(FileInputStream(archiveFile), 64 * 1024).use { bis ->
            val dis = DataInputStream(bis)
            val magic = ByteArray(5)
            dis.readFully(magic, 0, 4)

            val entries: List<Nxs6Entry>
            val payloadStream: InputStream

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

                for (i in 0 until nExtensions) {
                    val extId = readU16LE(dis)
                    val extSize = readU32LE(dis)
                    when (extId) {
                        EXT_TOC -> {
                            val tocBytes = ByteArray(extSize.toInt())
                            dis.readFully(tocBytes)
                            parsedToc = parseV2Toc(tocBytes)
                        }
                        EXT_APK_MANIFEST -> {
                            val manifestBytes = ByteArray(extSize.toInt())
                            dis.readFully(manifestBytes)
                            apkManifests = parseApkManifests(manifestBytes)
                        }
                        EXT_SHA256 -> {
                            val shaBytes = ByteArray(extSize.toInt())
                            dis.readFully(shaBytes)
                            sha256Map = parseSha256Map(shaBytes)
                        }
                        EXT_PAYLOAD -> {
                            foundPayload = true
                            break
                        }
                        else -> {
                            skipExact(dis, extSize)
                        }
                    }
                }
                require(foundPayload) { "Archivo NXS6 sin EXT_PAYLOAD" }
                entries = parsedToc ?: error("Archivo NXS6 sin EXT_TOC")
                payloadStream = XZInputStream(dis)
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
                payloadStream = XZInputStream(dis)
            }

            val sorted = entries.sortedBy { it.offset }
            val totalBytes = sorted.sumOf { it.preSize }.coerceAtLeast(1L)
            var streamPos = 0L
            val buffer = ByteArray(64 * 1024)

            payloadStream.use { xz ->
                for (entry in sorted) {
                    if (entry.offset > streamPos) {
                        skipExact(xz, entry.offset - streamPos)
                        streamPos = entry.offset
                    }

                    val outFile = safeResolve(targetDir, canonicalTarget, entry.name)
                    outFile.parentFile?.mkdirs()

                    val digest = if (sha256Map.containsKey(entry.name)) {
                        MessageDigest.getInstance("SHA-256")
                    } else null

                    var remaining = entry.preSize
                    FileOutputStream(outFile).use { fos ->
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val read = xz.read(buffer, 0, toRead)
                            if (read == -1) throw EOFException("Fin inesperado del flujo NXS6")
                            fos.write(buffer, 0, read)
                            digest?.update(buffer, 0, read)
                            remaining -= read
                            streamPos += read
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

                    val isVirtualApkEntry = entry.name.startsWith("__nx6r_apk__/")
                    if (!isVirtualApkEntry) {
                        when {
                            outFile.name.endsWith(".apk", ignoreCase = true) -> apkFiles.add(outFile)
                            outFile.name.endsWith(".obb", ignoreCase = true) -> obbFiles.add(outFile)
                            outFile.name.endsWith(".apks", ignoreCase = true) ||
                                outFile.name.endsWith(".xapk", ignoreCase = true) ||
                                outFile.name.endsWith(".zip", ignoreCase = true) -> nestedZipFiles.add(outFile)
                        }
                    }

                    val pct = ((streamPos * 100) / totalBytes).toInt().coerceIn(0, 100)
                    onProgress(pct, outFile.name)
                }
            }
        }

        // Reconstrucción NX6R APK-aware: reempaqueta las entradas en APK(s) firmados
        if (apkManifests.isNotEmpty()) {
            val virtualRoot = File(targetDir, "__nx6r_apk__")
            for (manifest in apkManifests) {
                val reconstructed = reconstructAndSignApk(
                    manifest = manifest,
                    entriesDir = File(virtualRoot, manifest.apkName),
                    outputDir = targetDir,
                    signingContext = signingContext
                )
                apkFiles.add(reconstructed)
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
        entriesDir: File,
        outputDir: File,
        signingContext: Context?
    ): File {
        val outApk = safeResolve(outputDir, outputDir.canonicalPath, manifest.apkName)
        outApk.parentFile?.mkdirs()
        val unsignedApk = File(outApk.parentFile, "${outApk.name}.unsigned")
        if (unsignedApk.exists()) unsignedApk.delete()

        val buffer = ByteArray(64 * 1024)
        ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
            for (item in manifest.entries) {
                val src = safeResolve(entriesDir, entriesDir.canonicalPath, item.entryName)
                if (!src.exists()) continue

                val entry = ZipEntry(item.entryName)
                if (item.method == ZipEntry.STORED) {
                    entry.method = ZipEntry.STORED
                    entry.size = src.length()
                    entry.crc = computeCrc32(src, buffer)
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
            bb.get() // preprocessor
            bb.get() // chunk_id
            val chunkOffset = bb.long
            out.add(Nxs6Entry(name = name, preSize = preSize, offset = chunkOffset))
        }
        return out
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

    private fun safeResolve(targetDir: File, canonicalTarget: String, entryName: String): File {
        val cleanName = entryName.replace('\\', '/').trimStart('/')
        val outFile = File(targetDir, cleanName)
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
}
