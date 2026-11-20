package com.hextapp.android.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hextapp.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Resolving : DownloadState
    data class Downloading(val percent: Int, val bytesRead: Long, val totalBytes: Long) : DownloadState
    data class Completed(val file: File) : DownloadState
    data class Error(val message: String) : DownloadState
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
    val lastModified: Long
)

class ApkDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    private val _localApks = MutableStateFlow<List<LocalApkFile>>(emptyList())
    val localApks: StateFlow<List<LocalApkFile>> = _localApks.asStateFlow()

    init {
        refreshLocalApks()
    }

    private fun apkDirectory(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "apks").apply { mkdirs() }
    }

    fun refreshLocalApks() {
        val files = apkDirectory().listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                LocalApkFile(
                    file = it,
                    name = it.nameWithoutExtension,
                    sizeBytes = it.length(),
                    lastModified = it.lastModified()
                )
            }
            .orEmpty()
        _localApks.value = files
    }

    fun deleteLocalApk(file: File) {
        if (file.exists()) {
            file.delete()
            refreshLocalApks()
        }
    }

    fun getExistingApk(slug: String, version: String): File? {
        val safeName = sanitizeFilename("${slug}-${version}.apk")
        val target = File(apkDirectory(), safeName)
        return if (target.exists() && target.length() > 0L) target else null
    }

    suspend fun downloadApk(
        itemId: String,
        slug: String,
        version: String,
        fallbackUrl: String? = null,
        onRegistered: suspend () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        updateState(itemId, DownloadState.Resolving)
        try {
            val resolvedUrl = resolveDirectDownloadUrl(itemId) ?: fallbackUrl
            if (resolvedUrl.isNullOrBlank()) {
                updateState(itemId, DownloadState.Error("No se encontró un enlace de descarga válido"))
                return@withContext
            }

            val safeName = sanitizeFilename("${slug}-${version}.apk")
            val tempFile = File(apkDirectory(), "$safeName.part")
            val finalFile = File(apkDirectory(), safeName)

            val request = Request.Builder().url(resolvedUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    updateState(itemId, DownloadState.Error("Error HTTP ${response.code} al descargar"))
                    return@withContext
                }
                val body = response.body ?: run {
                    updateState(itemId, DownloadState.Error("Respuesta vacía del servidor"))
                    return@withContext
                }

                val totalBytes = body.contentLength()
                var bytesRead = 0L
                val buffer = ByteArray(16 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val percent = if (totalBytes > 0) {
                                ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            updateState(itemId, DownloadState.Downloading(percent, bytesRead, totalBytes))
                        }
                    }
                }

                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
            }

            runCatching { onRegistered() }
            refreshLocalApks()
            updateState(itemId, DownloadState.Completed(finalFile))
        } catch (e: Exception) {
            updateState(itemId, DownloadState.Error(e.message ?: "Error al descargar el APK"))
        }
    }

    private fun resolveDirectDownloadUrl(itemId: String): String? {
        val url = "${BuildConfig.API_BASE_URL}/api/download/$itemId?json=1"
        val request = Request.Builder().url(url).build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<PrefetchResponse>(raw)
                parsed.data?.url
            }
        }.getOrNull()
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

    private fun updateState(itemId: String, state: DownloadState) {
        _downloads.update { current -> current + (itemId to state) }
    }

    private fun sanitizeFilename(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
