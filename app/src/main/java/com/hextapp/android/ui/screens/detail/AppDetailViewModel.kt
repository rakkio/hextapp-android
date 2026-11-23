package com.hextapp.android.ui.screens.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hextapp.android.BuildConfig
import com.hextapp.android.data.download.ApkDownloadManager
import com.hextapp.android.data.download.DownloadState
import com.hextapp.android.data.repository.AuthRepository
import com.hextapp.android.data.repository.CatalogRepository
import com.hextapp.android.graphql.GetItemDetailQuery
import com.hextapp.android.graphql.GetItemReviewsQuery
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.graphql.type.ContentType
import com.hextapp.android.graphql.type.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val item: GetItemDetailQuery.Item? = null,
    val similar: List<ItemSummary> = emptyList(),
    val reviews: List<GetItemReviewsQuery.Review> = emptyList(),
    val isFavorite: Boolean = false,
    val isBookmarked: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val snackbarMessage: String? = null
)

class AppDetailViewModel(
    private val slug: String,
    private val catalogRepository: CatalogRepository,
    private val authRepository: AuthRepository,
    private val apkDownloadManager: ApkDownloadManager
) : ViewModel() {

    private val itemFlow = MutableStateFlow<GetItemDetailQuery.Item?>(null)
    private val similarFlow = MutableStateFlow<List<ItemSummary>>(emptyList())
    private val reviewsFlow = MutableStateFlow<List<GetItemReviewsQuery.Review>>(emptyList())
    private val loadingFlow = MutableStateFlow(true)
    private val errorFlow = MutableStateFlow<String?>(null)
    private val messageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppDetailUiState> = combine(
        loadingFlow,
        errorFlow,
        itemFlow,
        similarFlow,
        reviewsFlow,
        authRepository.session,
        apkDownloadManager.downloads,
        messageFlow
    ) { values ->
        val loading = values[0] as Boolean
        val error = values[1] as String?
        val item = values[2] as GetItemDetailQuery.Item?
        @Suppress("UNCHECKED_CAST")
        val similar = values[3] as List<ItemSummary>
        @Suppress("UNCHECKED_CAST")
        val reviews = values[4] as List<GetItemReviewsQuery.Review>
        val session = values[5] as com.hextapp.android.data.repository.UserSession?
        @Suppress("UNCHECKED_CAST")
        val downloads = values[6] as Map<String, DownloadState>
        val message = values[7] as String?

        val itemId = item?.itemSummary?.id
        val existingFile = item?.let {
            apkDownloadManager.getExistingApk(it.itemSummary.slug, it.itemSummary.currentVersion)
        }
        val activeState = itemId?.let { downloads[it] }
        val resolvedDownloadState = when {
            activeState != null && activeState !is DownloadState.Idle -> activeState
            existingFile != null -> DownloadState.Completed(existingFile)
            else -> DownloadState.Idle
        }

        AppDetailUiState(
            isLoading = loading,
            error = error,
            item = item,
            similar = similar,
            reviews = reviews,
            isFavorite = itemId != null && session?.favorites?.contains(itemId) == true,
            isBookmarked = itemId != null && session?.bookmarks?.contains(itemId) == true,
            downloadState = resolvedDownloadState,
            snackbarMessage = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppDetailUiState())

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            loadingFlow.value = true
            errorFlow.value = null
            catalogRepository.getItemDetail(slug)
                .onSuccess { item ->
                    itemFlow.value = item
                    loadingFlow.value = false
                    if (item != null) {
                        val summary = item.itemSummary
                        launch {
                            catalogRepository.getSimilarItems(
                                contentType = summary.contentType.rawValue,
                                categoryId = summary.category?.id,
                                excludeId = summary.id
                            ).onSuccess { similarFlow.value = it }
                        }
                        launch {
                            catalogRepository.getItemReviews(summary.id)
                                .onSuccess { data -> reviewsFlow.value = data?.reviews.orEmpty() }
                        }
                    } else {
                        errorFlow.value = "APK no encontrada"
                    }
                }
                .onFailure {
                    loadingFlow.value = false
                    errorFlow.value = it.message ?: "Error al cargar la ficha"
                }
        }
    }

    fun onDownloadOrInstall(context: Context) {
        val item = itemFlow.value ?: return
        val summary = item.itemSummary
        val currentState = uiState.value.downloadState

        if (currentState is DownloadState.Completed) {
            viewModelScope.launch {
                apkDownloadManager.installWithNexus(context, currentState.file, summary.id)
                    .onSuccess { msg -> messageFlow.value = msg }
                    .onFailure { err -> messageFlow.value = err.message ?: "Error al instalar" }
            }
            return
        }
        if (currentState is DownloadState.Downloading ||
            currentState is DownloadState.Resolving ||
            currentState is DownloadState.Extracting ||
            currentState is DownloadState.Installing
        ) {
            return
        }

        val fallbackUrl = item.currentDownloadUrl
            ?: item.downloadUrl
            ?: item.versions.firstOrNull { it.isActive }?.downloadLinks?.primary?.url

        viewModelScope.launch {
            val downloadedFile = apkDownloadManager.downloadApk(
                itemId = summary.id,
                itemName = summary.name,
                slug = summary.slug,
                version = summary.currentVersion,
                fallbackUrl = fallbackUrl,
                onRegistered = { authRepository.registerDownload(summary.id) }
            )
            if (downloadedFile != null) {
                apkDownloadManager.installWithNexus(context, downloadedFile, summary.id)
                    .onSuccess { msg -> messageFlow.value = msg }
                    .onFailure { err -> messageFlow.value = err.message ?: "Error al instalar" }
            }
        }
    }

    fun cancelDownload() {
        val itemId = itemFlow.value?.itemSummary?.id ?: return
        apkDownloadManager.cancelDownload(itemId)
    }

    fun dismissBrowserDownload() {
        val itemId = itemFlow.value?.itemSummary?.id ?: return
        apkDownloadManager.dismissRequiresBrowser(itemId)
    }

    fun onWebViewDownloadCaptured(
        context: Context,
        url: String,
        cookie: String?,
        userAgent: String?,
        referer: String?
    ) {
        val item = itemFlow.value ?: return
        val summary = item.itemSummary
        viewModelScope.launch {
            val downloadedFile = apkDownloadManager.downloadFromWebViewUrl(
                itemId = summary.id,
                itemName = summary.name,
                slug = summary.slug,
                version = summary.currentVersion,
                url = url,
                cookie = cookie,
                userAgent = userAgent,
                referer = referer,
                onRegistered = { authRepository.registerDownload(summary.id) }
            )
            if (downloadedFile != null) {
                apkDownloadManager.installWithNexus(context, downloadedFile, summary.id)
                    .onSuccess { msg -> messageFlow.value = msg }
                    .onFailure { err -> messageFlow.value = err.message ?: "Error al instalar" }
            }
        }
    }

    fun toggleFavorite() {
        val itemId = itemFlow.value?.itemSummary?.id ?: return
        viewModelScope.launch {
            authRepository.toggleFavorite(itemId)
                .onSuccess { added ->
                    messageFlow.value = if (added) "Añadido a favoritos" else "Eliminado de favoritos"
                }
                .onFailure {
                    messageFlow.value = it.message ?: "Inicia sesión en Perfil"
                }
        }
    }

    fun toggleBookmark() {
        val itemId = itemFlow.value?.itemSummary?.id ?: return
        viewModelScope.launch {
            authRepository.toggleBookmark(itemId)
                .onSuccess { added ->
                    messageFlow.value = if (added) "Guardado en biblioteca" else "Eliminado de guardados"
                }
                .onFailure {
                    messageFlow.value = it.message ?: "Inicia sesión en Perfil"
                }
        }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    companion object {
        fun factory(
            slug: String,
            catalogRepo: CatalogRepository,
            authRepo: AuthRepository,
            downloadManager: ApkDownloadManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppDetailViewModel(slug, catalogRepo, authRepo, downloadManager) as T
        }
    }
}
