package com.hextapp.android.data.update

import android.content.Context
import com.hextapp.android.BuildConfig
import com.hextapp.android.data.download.ApkDownloadManager
import com.hextapp.android.data.download.DownloadState
import com.hextapp.android.data.local.UpdatePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class AppReleaseInfo(
    val version: String,
    val tagName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val releaseNotes: String
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: AppReleaseInfo) : UpdateUiState
    data class Downloading(val release: AppReleaseInfo, val percent: Int) : UpdateUiState
    data class ReadyToInstall(val release: AppReleaseInfo, val file: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val body: String = "",
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
private data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

object AppUpdateComparator {
    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    fun isNewerVersion(remoteRaw: String, currentRaw: String): Boolean {
        val remoteParts = normalize(remoteRaw).split("-", "+").first().split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = normalize(currentRaw).split("-", "+").first().split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

class AppUpdateManager(
    private val okHttpClient: OkHttpClient,
    private val apkDownloadManager: ApkDownloadManager,
    private val updatePreferences: UpdatePreferences
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/rakkio/hextapp-android/releases/latest"
        private const val SELF_UPDATE_ITEM_ID = "__hextapp_self_update__"
    }

    suspend fun checkForUpdates(
        autoTrigger: Boolean,
        activityContextForAutoInstall: Context? = null
    ) {
        if (_uiState.value is UpdateUiState.Checking || _uiState.value is UpdateUiState.Downloading) {
            return
        }

        _uiState.value = UpdateUiState.Checking
        val release = fetchLatestRelease()
        if (release == null) {
            _uiState.value = if (autoTrigger) UpdateUiState.Idle else UpdateUiState.Error("No se pudo comprobar actualizaciones")
            return
        }

        if (!AppUpdateComparator.isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
            _uiState.value = if (autoTrigger) UpdateUiState.Idle else UpdateUiState.UpToDate
            return
        }

        val autoEnabled = updatePreferences.autoUpdateEnabled.first()
        if (autoEnabled) {
            downloadAndInstall(release, activityContextForAutoInstall)
            return
        }

        val skipped = updatePreferences.skippedVersion.first()
        if (autoTrigger && skipped == release.version) {
            _uiState.value = UpdateUiState.Idle
            return
        }

        _uiState.value = UpdateUiState.Available(release)
    }

    suspend fun downloadAndInstall(release: AppReleaseInfo, activityContext: Context? = null) {
        val existing = apkDownloadManager.getExistingApk("hextapp-android", release.version)
        if (existing != null) {
            _uiState.value = UpdateUiState.ReadyToInstall(release, existing)
            activityContext?.let { apkDownloadManager.installApk(it, existing) }
            return
        }

        _uiState.value = UpdateUiState.Downloading(release, 0)
        apkDownloadManager.downloadApk(
            itemId = SELF_UPDATE_ITEM_ID,
            slug = "hextapp-android",
            version = release.version,
            fallbackUrl = release.apkUrl
        )

        val finalState = apkDownloadManager.downloads.value[SELF_UPDATE_ITEM_ID]
        when (finalState) {
            is DownloadState.Completed -> {
                _uiState.value = UpdateUiState.ReadyToInstall(release, finalState.file)
                activityContext?.let { apkDownloadManager.installApk(it, finalState.file) }
            }
            is DownloadState.Error -> {
                _uiState.value = UpdateUiState.Error(finalState.message)
            }
            else -> {
                _uiState.value = UpdateUiState.Error("La descarga de la actualización no se completó")
            }
        }
    }

    fun installDownloadedUpdate(activityContext: Context, file: File) {
        apkDownloadManager.installApk(activityContext, file)
    }

    suspend fun skipVersion(version: String) {
        updatePreferences.setSkippedVersion(version)
        _uiState.value = UpdateUiState.Idle
    }

    fun dismissDialog() {
        _uiState.value = UpdateUiState.Idle
    }

    private suspend fun fetchLatestRelease(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "hextapp-android/${BuildConfig.VERSION_NAME}")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string().orEmpty()
                val dto = json.decodeFromString<GitHubReleaseDto>(raw)
                val apkAsset = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@use null
                val version = AppUpdateComparator.normalize(dto.tagName)
                if (version.isBlank() || apkAsset.browserDownloadUrl.isBlank()) return@use null

                AppReleaseInfo(
                    version = version,
                    tagName = dto.tagName,
                    apkUrl = apkAsset.browserDownloadUrl,
                    apkSizeBytes = apkAsset.size,
                    releaseNotes = dto.body.ifBlank { "Nueva versión disponible de Hextapp Android." }
                )
            }
        }.getOrNull()
    }
}
