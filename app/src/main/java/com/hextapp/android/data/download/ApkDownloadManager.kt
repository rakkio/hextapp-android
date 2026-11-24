package com.hextapp.android.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hextapp.android.BuildConfig
import com.hextapp.android.data.nexus.ArchiveKind
import com.hextapp.android.data.nexus.NexusEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Resolving : DownloadState
    data class Downloading(val percent: Int, val bytesRead: Long, val totalBytes: Long) : DownloadState
    data class Extracting(val percent: Int, val fileName: String) : DownloadState
    data object Installing : DownloadState
    data class RequiresBrowser(val url: String, val itemName: String) : DownloadState
    data class Completed(val file: File) : DownloadState
    data class Error(val message: String, val fallbackUrl: String? = null) : DownloadState
}

@Serializable
private data class PrefetchResponse(
    val success: Boolean = false,
    val data: ResolvedDownloadData? = null,
    val error: String? = null
)

@Serializable
private data class ResolvedDownloadData(
    val url: String? = null,
    val filename: String? = null,
    val size: String? = null
)

data class LocalApkFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val archiveKind: ArchiveKind = ArchiveKind.SINGLE_APK,
    val isInstalled: Boolean = false
)

data class VaultItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

object ApkDownloadValidation {
    const val MIN_VALID_APK_BYTES = 50 * 1024L

    fun isNonBinaryContentType(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return false
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return normalized.startsWith("text/") ||
            normalized == "application/json" ||
            normalized == "application/xhtml+xml" ||
            normalized == "application/javascript" ||
            normalized == "application/xml"
    }

    fun hasZipMagicBytes(header: ByteArray, length: Int): Boolean {
        if (length < 4) return false
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
    }

    fun isValidZipOrApkFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < MIN_VALID_APK_BYTES) {
            return false
        }
        return runCatching {
            val buffer = ByteArray(5)
            FileInputStream(file).use { input ->
                val read = input.read(buffer)
                hasZipMagicBytes(buffer, read) || NexusEngine.hasNxsMagicBytes(buffer, read)
            }
        }.getOrDefault(false)
    }

    fun isLikelyHtmlPageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("gofile.io/d/") ||
            lower.contains("steamrip.com/") ||
            lower.contains("1fichier.com/") ||
            lower.contains("mega.nz/") ||
            lower.contains("mediafire.com/file/")
    }
}

class ApkDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val _localApks = MutableStateFlow<List<LocalApkFile>>(emptyList())
    val localApks: StateFlow<List<LocalApkFile>> = _localApks.asStateFlow()

    private val _vaultItems = MutableStateFlow<List<VaultItem>>(emptyList())
    val vaultItems: StateFlow<List<VaultItem>> = _vaultItems.asStateFlow()

    companion object {
        private const val INSTALLED_TTL_MS = 24L * 60L * 60L * 1000L // 24 hours after install
        private const val UNINSTALLED_TTL_MS = 7L * 24L * 60L * 60L * 1000L // 7 days max cache
        private const val PART_TTL_MS = 2L * 60L * 60L * 1000L // 2 hours for stale .part
    }

    init {
        cleanupExpiredFiles()
        refreshLocalApks()
    }

    private fun apkDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "apks").apply { mkdirs() }
    }

    private fun isPackageInstalled(apkFile: File): Boolean {
        if (!apkFile.name.endsWith(".apk", ignoreCase = true)) return false
        return runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            } ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    info.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(info.packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun cleanupExpiredFiles(nowMs: Long = System.currentTimeMillis()) {
        val apksDir = apkDirectory()
        for (file in apksDir.listFiles().orEmpty()) {
            if (!file.isFile) continue
            val age = nowMs - file.lastModified()
            if (file.name.endsWith(".part", ignoreCase = true)) {
                if (age >= PART_TTL_MS) file.delete()
                continue
            }
            if (!isSupportedArchiveName(file.name)) continue
            val installed = isPackageInstalled(file)
            if ((installed && age >= INSTALLED_TTL_MS) || age >= UNINSTALLED_TTL_MS) {
                file.delete()
                extractedDirectory(file.nameWithoutExtension).deleteRecursively()
            }
        }

        val extractedRoot = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "extracted"
        )
        if (extractedRoot.exists()) {
            for (dir in extractedRoot.listFiles().orEmpty()) {
                val age = nowMs - dir.lastModified()
                if (age >= UNINSTALLED_TTL_MS) {
                    dir.deleteRecursively()
                }
            }
        }
    }

    private fun isSupportedArchiveName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".apk") ||
            lower.endsWith(".apks") ||
            lower.endsWith(".xapk") ||
            lower.endsWith(".zip") ||
            lower.endsWith(".nxs6") ||
            lower.endsWith(".nx6r")
    }

    private fun extractedDirectory(name: String): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(File(base, "extracted"), sanitizeFilename(name))
    }

    private fun vaultDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "nexus_vault").apply { mkdirs() }
    }

    fun refreshVault() {
        val items = vaultDirectory().listFiles().orEmpty()
            .filter { it.isFile && (it.name.endsWith(".nx6r", ignoreCase = true) || it.name.endsWith(".nxs6", ignoreCase = true)) }
            .map { VaultItem(file = it, name = it.nameWithoutExtension, sizeBytes = it.length(), lastModified = it.lastModified()) }
            .sortedByDescending { it.lastModified }
        _vaultItems.value = items
    }

    suspend fun archiveToVault(sourceFile: File, deleteOriginal: Boolean = true): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outFile = File(vaultDirectory(), "${sanitizeFilename(sourceFile.nameWithoutExtension)}.nx6r")
            if (sourceFile.name.endsWith(".nx6r", ignoreCase = true) || sourceFile.name.endsWith(".nxs6", ignoreCase = true)) {
                sourceFile.copyTo(outFile, overwrite = true)
            } else {
                NexusEngine.packNx6r(sourceFile, outFile, preset = 4)
            }
            if (deleteOriginal && sourceFile.exists() && sourceFile.canonicalPath != outFile.canonicalPath) {
                sourceFile.delete()
            }
            refreshLocalApks()
            refreshVault()
            outFile
        }
    }

    suspend fun restoreFromVault(activityContext: Context, vaultFile: File): Result<String> {
        return installWithNexus(activityContext, vaultFile)
    }

    fun deleteFromVault(vaultFile: File) {
        if (vaultFile.exists()) {
            vaultFile.delete()
            refreshVault()
        }
    }

    fun refreshLocalApks() {
        val allFiles = apkDirectory().listFiles().orEmpty()
        val validFiles = mutableListOf<LocalApkFile>()
        for (file in allFiles) {
            if (!file.isFile || !isSupportedArchiveName(file.name)) continue
            if (!ApkDownloadValidation.isValidZipOrApkFile(file)) {
                // Purge previously saved corrupt/3KB HTML files
                file.delete()
                continue
            }
            validFiles.add(
                LocalApkFile(
                    file = file,
                    name = file.nameWithoutExtension,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    archiveKind = NexusEngine.inspectArchive(file),
                    isInstalled = isPackageInstalled(file)
                )
            )
        }
        _localApks.value = validFiles.sortedByDescending { it.lastModified }
    }

    fun deleteLocalApk(file: File) {
        if (file.exists()) {
            file.delete()
            extractedDirectory(file.nameWithoutExtension).deleteRecursively()
            refreshLocalApks()
        }
    }

    fun getExistingApk(slug: String, version: String): File? {
        val base = sanitizeFilename("${slug}-${version}")
        val candidates = listOf("$base.nx6r", "$base.nxs6", "$base.apk", "$base.apks", "$base.xapk", "$base.zip")
        for (candidate in candidates) {
            val target = File(apkDirectory(), candidate)
            if (!target.exists()) continue
            if (!ApkDownloadValidation.isValidZipOrApkFile(target)) {
                target.delete()
                continue
            }
            return target
        }
        return null
    }

    fun cancelDownload(itemId: String) {
        activeCalls.remove(itemId)?.cancel()
        updateState(itemId, DownloadState.Idle)
    }

    fun dismissRequiresBrowser(itemId: String) {
        if (_downloads.value[itemId] is DownloadState.RequiresBrowser) {
            updateState(itemId, DownloadState.Idle)
        }
    }

    suspend fun downloadApk(
        itemId: String,
        itemName: String,
        slug: String,
        version: String,
        fallbackUrl: String? = null,
        onRegistered: suspend () -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        updateState(itemId, DownloadState.Resolving)
        try {
            val resolvedUrl = resolveDirectDownloadUrl(itemId) ?: fallbackUrl
            if (resolvedUrl.isNullOrBlank()) {
                updateState(itemId, DownloadState.Error("No se encontró un enlace de descarga válido"))
                return@withContext null
            }

            if (ApkDownloadValidation.isLikelyHtmlPageUrl(resolvedUrl)) {
                updateState(itemId, DownloadState.RequiresBrowser(resolvedUrl, itemName))
                return@withContext null
            }

            // If backend resolved an http:// link (e.g. srv-watch-dwn*.apk.watch), stream via HTTPS backend proxy
            val targetUrl = if (resolvedUrl.startsWith("http://", ignoreCase = true)) {
                "${BuildConfig.API_BASE_URL}/api/download/$itemId"
            } else {
                resolvedUrl
            }

            executeDownload(
                itemId = itemId,
                itemName = itemName,
                slug = slug,
                version = version,
                targetUrl = targetUrl,
                browserFallbackUrl = resolvedUrl,
                headers = emptyMap(),
                allowBrowserFallback = true,
                onRegistered = onRegistered
            )
        } catch (e: CancellationException) {
            updateState(itemId, DownloadState.Idle)
            throw e
        } catch (e: Exception) {
            updateState(itemId, DownloadState.Error(e.message ?: "Error al descargar el APK", fallbackUrl))
            null
        }
    }

    suspend fun downloadFromWebViewUrl(
        itemId: String,
        itemName: String,
        slug: String,
        version: String,
        url: String,
        cookie: String?,
        userAgent: String?,
        referer: String?,
        onRegistered: suspend () -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        updateState(itemId, DownloadState.Resolving)
        try {
            val headers = buildMap {
                if (!cookie.isNullOrBlank()) put("Cookie", cookie)
                if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
                if (!referer.isNullOrBlank()) put("Referer", referer)
            }
            executeDownload(
                itemId = itemId,
                itemName = itemName,
                slug = slug,
                version = version,
                targetUrl = url,
                browserFallbackUrl = referer ?: url,
                headers = headers,
                allowBrowserFallback = false,
                onRegistered = onRegistered
            )
        } catch (e: CancellationException) {
            updateState(itemId, DownloadState.Idle)
            throw e
        } catch (e: Exception) {
            updateState(itemId, DownloadState.Error(e.message ?: "Error al descargar el APK", referer ?: url))
            null
        }
    }

    private suspend fun executeDownload(
        itemId: String,
        itemName: String,
        slug: String,
        version: String,
        targetUrl: String,
        browserFallbackUrl: String,
        headers: Map<String, String>,
        allowBrowserFallback: Boolean,
        onRegistered: suspend () -> Unit
    ): File? {
        val safeName = sanitizeFilename("${slug}-${version}.apk")
        val tempFile = File(apkDirectory(), "$safeName.part")
        val finalFile = File(apkDirectory(), safeName)
        val existingBytes = if (tempFile.exists() && tempFile.length() > 0L) tempFile.length() else 0L

        val requestBuilder = Request.Builder().url(targetUrl)
        headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = okHttpClient.newCall(requestBuilder.build())
        activeCalls[itemId] = call
        try {
            call.execute().use { response ->
                val contentType = response.header("Content-Type")
                android.util.Log.d("HextappDownload", "url=$targetUrl code=${response.code} type=$contentType len=${response.body?.contentLength()} resume=$existingBytes")
                if (response.code == 416 && existingBytes > 0L) {
                    tempFile.delete()
                    updateState(
                        itemId,
                        DownloadState.Error("Archivo parcial incompatible; reintenta la descarga.", browserFallbackUrl)
                    )
                    return null
                }
                if (!response.isSuccessful) {
                    updateState(
                        itemId,
                        DownloadState.Error("Error HTTP ${response.code} al descargar", browserFallbackUrl)
                    )
                    return null
                }

                if (ApkDownloadValidation.isNonBinaryContentType(contentType)) {
                    tempFile.delete()
                    if (allowBrowserFallback) {
                        updateState(itemId, DownloadState.RequiresBrowser(browserFallbackUrl, itemName))
                    } else {
                        updateState(
                            itemId,
                            DownloadState.Error(
                                "El servidor devolvió una página ($contentType) en lugar del APK.",
                                browserFallbackUrl
                            )
                        )
                    }
                    return null
                }

                val body = response.body ?: run {
                    updateState(itemId, DownloadState.Error("Respuesta vacía del servidor", browserFallbackUrl))
                    return null
                }

                val isResumed = response.code == 206 && existingBytes > 0L
                val baseOffset = if (isResumed) existingBytes else 0L
                val remainingLen = body.contentLength()
                val totalBytes = if (remainingLen > 0L) baseOffset + remainingLen else -1L
                var bytesRead = baseOffset
                val buffer = ByteArray(16 * 1024)
                var lastEmitTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile, isResumed).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 120 || (totalBytes > 0 && bytesRead >= totalBytes)) {
                                lastEmitTime = now
                                val percent = if (totalBytes > 0) {
                                    ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                updateState(itemId, DownloadState.Downloading(percent, bytesRead, totalBytes))
                            }
                        }
                    }
                }

                if (!ApkDownloadValidation.isValidZipOrApkFile(tempFile)) {
                    val invalidSize = tempFile.length()
                    tempFile.delete()
                    if (allowBrowserFallback) {
                        updateState(itemId, DownloadState.RequiresBrowser(browserFallbackUrl, itemName))
                    } else {
                        updateState(
                            itemId,
                            DownloadState.Error(
                                "Archivo inválido o incompleto (${invalidSize} bytes). Prueba abrirlo en el navegador.",
                                browserFallbackUrl
                            )
                        )
                    }
                    return null
                }

                if (finalFile.exists()) finalFile.delete()
                val resolvedFinalFile = if (NexusEngine.isNxs6File(tempFile)) {
                    File(apkDirectory(), sanitizeFilename("${slug}-${version}.nxs6")).also {
                        if (it.exists()) it.delete()
                    }
                } else {
                    finalFile
                }
                if (!tempFile.renameTo(resolvedFinalFile)) {
                    tempFile.copyTo(resolvedFinalFile, overwrite = true)
                    tempFile.delete()
                }
                runCatching { onRegistered() }
                refreshLocalApks()
                updateState(itemId, DownloadState.Completed(resolvedFinalFile))
                return resolvedFinalFile
            }
        } catch (e: Exception) {
            if (call.isCanceled()) {
                tempFile.delete()
                updateState(itemId, DownloadState.Idle)
            } else {
                // Conservar tempFile (.part) para reanudar mediante HTTP Range en el siguiente intento
                updateState(itemId, DownloadState.Error(e.message ?: "Error al descargar el APK", browserFallbackUrl))
            }
            return null
        } finally {
            activeCalls.remove(itemId)
        }
    }

    private fun resolveDirectDownloadUrl(itemId: String): String? {
        val url = "${BuildConfig.API_BASE_URL}/api/download/$itemId?json=1"
        val request = Request.Builder().url(url).build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("json", ignoreCase = true)) return@use null
                val raw = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<PrefetchResponse>(raw)
                parsed.data?.url
            }
        }.getOrNull()
    }

    suspend fun installWithNexus(
        activityContext: Context,
        file: File,
        itemId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val kind = NexusEngine.inspectArchive(file)

            if (kind != ArchiveKind.GENERIC_ZIP &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activityContext.packageManager.canRequestPackageInstalls()
            ) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activityContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                withContext(Dispatchers.Main) {
                    activityContext.startActivity(settingsIntent)
                }
                return@runCatching "Activa 'Instalar aplicaciones desconocidas' para Hextapp"
            }

            when (kind) {
                ArchiveKind.SINGLE_APK -> {
                    checkApkCompatibility(file)?.let { reason ->
                        return@withContext Result.failure(IllegalStateException(reason))
                    }
                    withContext(Dispatchers.Main) {
                        launchSingleApkInstaller(activityContext, file)
                    }
                    "Abriendo instalador..."
                }
                ArchiveKind.ZIP_WITH_SINGLE_APK -> {
                    if (itemId != null) updateState(itemId, DownloadState.Extracting(0, file.name))
                    val outDir = extractedDirectory(file.nameWithoutExtension)
                    val extracted = NexusEngine.extractArchive(file, outDir) { pct, entry ->
                        if (itemId != null) updateState(itemId, DownloadState.Extracting(pct, entry))
                    }
                    val extractedApk = extracted.apkFiles.firstOrNull()
                        ?: error("No se encontró ningún APK dentro del archivo")
                    val cleanApk = promoteExtractedApk(extractedApk, file, outDir)
                    if (itemId != null) updateState(itemId, DownloadState.Completed(cleanApk))
                    checkApkCompatibility(cleanApk)?.let { reason ->
                        return@withContext Result.failure(IllegalStateException(reason))
                    }
                    withContext(Dispatchers.Main) {
                        launchSingleApkInstaller(activityContext, cleanApk)
                    }
                    "APK extraído; comprimido eliminado"
                }
                ArchiveKind.SPLIT_APKS -> {
                    if (itemId != null) updateState(itemId, DownloadState.Extracting(0, file.name))
                    val outDir = extractedDirectory(file.nameWithoutExtension)
                    val extracted = NexusEngine.extractArchive(file, outDir) { pct, entry ->
                        if (itemId != null) updateState(itemId, DownloadState.Extracting(pct, entry))
                    }
                    if (extracted.obbFiles.isNotEmpty()) {
                        NexusEngine.installObbFiles(extracted.obbFiles)
                    }
                    file.delete()
                    refreshLocalApks()
                    if (itemId != null) updateState(itemId, DownloadState.Installing)
                    NexusEngine.installSplitApks(activityContext, extracted.apkFiles).getOrThrow()
                    if (itemId != null) updateState(itemId, DownloadState.Idle)
                    "Instalando ${extracted.apkFiles.size} APKs divididos con Nexus..."
                }
                ArchiveKind.NXS6_ARCHIVE -> {
                    if (itemId != null) updateState(itemId, DownloadState.Extracting(0, file.name))
                    val outDir = extractedDirectory(file.nameWithoutExtension)
                    val extracted = NexusEngine.extractNxs6(file, outDir, activityContext) { pct, entry ->
                        if (itemId != null) updateState(itemId, DownloadState.Extracting(pct, entry))
                    }
                    val obbCount = if (extracted.obbFiles.isNotEmpty()) NexusEngine.installObbFiles(extracted.obbFiles) else 0
                    val obbWarning = if (extracted.obbFiles.isNotEmpty() && obbCount < extracted.obbFiles.size) {
                        " (aviso: Android 11+ bloqueó copiar ${extracted.obbFiles.size - obbCount} OBB a Android/obb)"
                    } else ""
                    when {
                        extracted.apkFiles.size > 1 -> {
                            file.delete()
                            refreshLocalApks()
                            if (itemId != null) updateState(itemId, DownloadState.Installing)
                            NexusEngine.installSplitApks(activityContext, extracted.apkFiles).getOrThrow()
                            if (itemId != null) updateState(itemId, DownloadState.Idle)
                            (if (obbCount > 0) "Instalando ${extracted.apkFiles.size} APKs + $obbCount OBB desde NX6R..." else "Instalando ${extracted.apkFiles.size} APKs desde NXS6...") + obbWarning
                        }
                        extracted.apkFiles.size == 1 -> {
                            val cleanApk = promoteExtractedApk(extracted.apkFiles.first(), file, outDir)
                            if (itemId != null) updateState(itemId, DownloadState.Completed(cleanApk))
                            checkApkCompatibility(cleanApk)?.let { reason ->
                                return@withContext Result.failure(IllegalStateException(reason))
                            }
                            withContext(Dispatchers.Main) {
                                launchSingleApkInstaller(activityContext, cleanApk)
                            }
                            "APK descomprimido; .nxs6 eliminado"
                        }
                        else -> {
                            file.delete()
                            refreshLocalApks()
                            if (itemId != null) updateState(itemId, DownloadState.Idle)
                            "NXS6 descomprimido en ${outDir.name}/"
                        }
                    }
                }
                ArchiveKind.GENERIC_ZIP -> {
                    if (itemId != null) updateState(itemId, DownloadState.Extracting(0, file.name))
                    val outDir = extractedDirectory(file.nameWithoutExtension)
                    NexusEngine.extractArchive(file, outDir) { pct, entry ->
                        if (itemId != null) updateState(itemId, DownloadState.Extracting(pct, entry))
                    }
                    if (itemId != null) updateState(itemId, DownloadState.Completed(file))
                    "Archivo descomprimido en ${outDir.name}/"
                }
            }
        }.onFailure {
            if (itemId != null) updateState(itemId, DownloadState.Completed(file))
        }
    }

    private fun promoteExtractedApk(extractedApk: File, sourceArchive: File, outDir: File): File {
        val cleanApk = File(apkDirectory(), sanitizeFilename("${sourceArchive.nameWithoutExtension}.apk"))
        if (cleanApk.exists()) cleanApk.delete()
        if (!extractedApk.renameTo(cleanApk)) {
            extractedApk.copyTo(cleanApk, overwrite = true)
        }
        sourceArchive.delete()
        outDir.deleteRecursively()
        refreshLocalApks()
        return cleanApk
    }

    private fun checkApkCompatibility(apkFile: File): String? {
        if (!apkFile.name.endsWith(".apk", ignoreCase = true)) return null
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        }.getOrNull() ?: return "El archivo APK está dañado o no se puede leer"

        val targetSdk = info.applicationInfo?.targetSdkVersion ?: 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && targetSdk in 1..23) {
            return "APK incompatible con Android ${Build.VERSION.RELEASE}: requiere targetSdk >= 24 (este APK usa SDK $targetSdk)."
        }
        return null
    }

    private fun launchSingleApkInstaller(activityContext: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            activityContext,
            "${activityContext.packageName}.fileprovider",
            file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activityContext.startActivity(installIntent)
    }

    fun installApk(activityContext: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activityContext.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activityContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityContext.startActivity(settingsIntent)
            return
        }
        launchSingleApkInstaller(activityContext, file)
    }

    private fun updateState(itemId: String, state: DownloadState) {
        _downloads.update { current -> current + (itemId to state) }
    }

    fun sanitizeFilename(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
