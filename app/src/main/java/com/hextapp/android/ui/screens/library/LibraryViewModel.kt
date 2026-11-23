package com.hextapp.android.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hextapp.android.data.download.ApkDownloadManager
import com.hextapp.android.data.download.DownloadState
import com.hextapp.android.data.download.LocalApkFile
import com.hextapp.android.data.download.VaultItem
import com.hextapp.android.data.repository.AuthRepository
import com.hextapp.android.data.repository.CatalogRepository
import com.hextapp.android.data.repository.UserSession
import com.hextapp.android.graphql.fragment.ItemSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class LibraryUiState(
    val session: UserSession? = null,
    val favoriteItems: List<ItemSummary> = emptyList(),
    val bookmarkItems: List<ItemSummary> = emptyList(),
    val localApks: List<LocalApkFile> = emptyList(),
    val vaultItems: List<VaultItem> = emptyList(),
    val activeDownloads: Map<String, DownloadState> = emptyMap(),
    val isLoading: Boolean = false,
    val isArchivingToVault: Boolean = false
)

class LibraryViewModel(
    private val authRepository: AuthRepository,
    private val catalogRepository: CatalogRepository,
    private val apkDownloadManager: ApkDownloadManager
) : ViewModel() {

    private val allAndroidItems = MutableStateFlow<List<ItemSummary>>(emptyList())
    private val isLoading = MutableStateFlow(false)
    private val isArchiving = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        authRepository.session,
        allAndroidItems,
        apkDownloadManager.localApks,
        apkDownloadManager.vaultItems,
        apkDownloadManager.downloads,
        isLoading,
        isArchiving
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val session = values[0] as UserSession?
        @Suppress("UNCHECKED_CAST")
        val catalog = values[1] as List<ItemSummary>
        @Suppress("UNCHECKED_CAST")
        val localApks = values[2] as List<LocalApkFile>
        @Suppress("UNCHECKED_CAST")
        val vaultItems = values[3] as List<VaultItem>
        @Suppress("UNCHECKED_CAST")
        val downloads = values[4] as Map<String, DownloadState>
        val loading = values[5] as Boolean
        val archiving = values[6] as Boolean

        val favIds = session?.favorites.orEmpty()
        val bmIds = session?.bookmarks.orEmpty()
        val inProgress = downloads.filterValues {
            it is DownloadState.Resolving || it is DownloadState.Downloading
        }
        LibraryUiState(
            session = session,
            favoriteItems = catalog.filter { favIds.contains(it.id) },
            bookmarkItems = catalog.filter { bmIds.contains(it.id) },
            localApks = localApks,
            vaultItems = vaultItems,
            activeDownloads = inProgress,
            isLoading = loading,
            isArchivingToVault = archiving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    init {
        refresh()
    }

    fun refresh() {
        apkDownloadManager.refreshLocalApks()
        apkDownloadManager.refreshVault()
        viewModelScope.launch {
            isLoading.value = true
            catalogRepository.getItems(page = 1, limit = 100).onSuccess { paged ->
                allAndroidItems.value = paged.items
            }
            isLoading.value = false
        }
    }

    fun archiveToVault(file: File) {
        viewModelScope.launch {
            isArchiving.value = true
            apkDownloadManager.archiveToVault(file, deleteOriginal = true)
            isArchiving.value = false
        }
    }

    fun restoreFromVault(context: Context, file: File) {
        viewModelScope.launch {
            apkDownloadManager.restoreFromVault(context, file)
        }
    }

    fun deleteFromVault(file: File) {
        apkDownloadManager.deleteFromVault(file)
    }

    fun installApk(context: Context, file: File) {
        viewModelScope.launch {
            apkDownloadManager.installWithNexus(context, file)
        }
    }

    fun deleteApk(file: File) {
        apkDownloadManager.deleteLocalApk(file)
    }

    fun cancelDownload(itemId: String) {
        apkDownloadManager.cancelDownload(itemId)
    }

    companion object {
        fun factory(
            authRepo: AuthRepository,
            catalogRepo: CatalogRepository,
            downloadManager: ApkDownloadManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(authRepo, catalogRepo, downloadManager) as T
        }
    }
}
