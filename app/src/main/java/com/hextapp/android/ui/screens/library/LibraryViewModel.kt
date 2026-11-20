package com.hextapp.android.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hextapp.android.data.download.ApkDownloadManager
import com.hextapp.android.data.download.DownloadState
import com.hextapp.android.data.download.LocalApkFile
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
    val activeDownloads: Map<String, DownloadState> = emptyMap(),
    val isLoading: Boolean = false
)

class LibraryViewModel(
    private val authRepository: AuthRepository,
    private val catalogRepository: CatalogRepository,
    private val apkDownloadManager: ApkDownloadManager
) : ViewModel() {

    private val allAndroidItems = MutableStateFlow<List<ItemSummary>>(emptyList())
    private val isLoading = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        authRepository.session,
        allAndroidItems,
        apkDownloadManager.localApks,
        apkDownloadManager.downloads,
        isLoading
    ) { session, catalog, localApks, downloads, loading ->
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
            activeDownloads = inProgress,
            isLoading = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    init {
        refresh()
    }

    fun refresh() {
        apkDownloadManager.refreshLocalApks()
        viewModelScope.launch {
            isLoading.value = true
            catalogRepository.getItems(page = 1, limit = 100).onSuccess { paged ->
                allAndroidItems.value = paged.items
            }
            isLoading.value = false
        }
    }

    fun installApk(context: Context, file: File) {
        apkDownloadManager.installApk(context, file)
    }

    fun deleteApk(file: File) {
        apkDownloadManager.deleteLocalApk(file)
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
