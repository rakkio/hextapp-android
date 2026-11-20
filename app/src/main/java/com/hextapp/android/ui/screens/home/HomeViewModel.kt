package com.hextapp.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hextapp.android.data.local.HomeCacheStorage
import com.hextapp.android.data.repository.CatalogRepository
import com.hextapp.android.graphql.GetCategoriesQuery
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.graphql.type.ItemSortBy
import com.hextapp.android.graphql.type.Platform
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOfflineFallback: Boolean = false,
    val error: String? = null,
    val categories: List<GetCategoriesQuery.Category> = emptyList(),
    val selectedCategorySlug: String? = null,
    val featured: List<ItemSummary> = emptyList(),
    val androidGames: List<ItemSummary> = emptyList(),
    val androidApps: List<ItemSummary> = emptyList(),
    val newestApks: List<ItemSummary> = emptyList(),
    val topRatedApks: List<ItemSummary> = emptyList()
)

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val homeCacheStorage: HomeCacheStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = homeCacheStorage.load()
            if (cached != null && (cached.androidGames.isNotEmpty() || cached.androidApps.isNotEmpty() || cached.newestApks.isNotEmpty())) {
                with(homeCacheStorage) {
                    val games = cached.androidGames.map { it.toGraphql() }
                    val apps = cached.androidApps.map { it.toGraphql() }
                    val newest = cached.newestApks.map { it.toGraphql() }
                    val topRated = cached.topRatedApks.map { it.toGraphql() }
                    val categories = cached.categories.map { it.toGraphql() }
                    val hero = (games.take(3) + apps.take(2) + newest.take(2)).distinctBy { it.id }.take(5)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            categories = categories,
                            featured = hero,
                            androidGames = games,
                            androidApps = apps,
                            newestApks = newest,
                            topRatedApks = topRated
                        )
                    }
                }
            }
            loadHome(isRefresh = cached != null)
        }
    }

    fun selectCategory(slug: String?) {
        _uiState.update { it.copy(selectedCategorySlug = slug) }
        loadHome(isRefresh = true)
    }

    fun loadHome(isRefresh: Boolean = true) {
        viewModelScope.launch {
            val hasExistingContent = _uiState.value.androidGames.isNotEmpty() ||
                _uiState.value.androidApps.isNotEmpty() ||
                _uiState.value.newestApks.isNotEmpty()

            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !hasExistingContent,
                    isRefreshing = isRefresh || hasExistingContent,
                    error = null
                )
            }

            val categorySlug = _uiState.value.selectedCategorySlug
            val categoriesDef = async { catalogRepository.getCategories(Platform.ANDROID) }
            val gamesDef = async {
                catalogRepository.getItems(
                    page = 1,
                    limit = 12,
                    platform = "ANDROID",
                    contentType = "apk",
                    categorySlug = categorySlug ?: "android-games",
                    sortBy = ItemSortBy.POPULARITY
                )
            }
            val appsDef = async {
                catalogRepository.getItems(
                    page = 1,
                    limit = 12,
                    platform = "ANDROID",
                    contentType = "apk",
                    categorySlug = categorySlug ?: "android-apps",
                    sortBy = ItemSortBy.POPULARITY
                )
            }
            val newestDef = async {
                catalogRepository.getItems(
                    page = 1,
                    limit = 12,
                    platform = "ANDROID",
                    contentType = "apk",
                    categorySlug = categorySlug,
                    sortBy = ItemSortBy.NEWEST
                )
            }
            val topRatedDef = async {
                catalogRepository.getItems(
                    page = 1,
                    limit = 10,
                    platform = "ANDROID",
                    contentType = "apk",
                    categorySlug = categorySlug,
                    sortBy = ItemSortBy.RATING
                )
            }

            val categoriesRes = categoriesDef.await()
            val gamesRes = gamesDef.await()
            val appsRes = appsDef.await()
            val newestRes = newestDef.await()
            val topRatedRes = topRatedDef.await()

            val newest = newestRes.getOrNull()?.items.orEmpty()
            val topRated = topRatedRes.getOrNull()?.items.orEmpty()
            var games = gamesRes.getOrNull()?.items.orEmpty()
            var apps = appsRes.getOrNull()?.items.orEmpty()

            // Fallback: if category-specific queries returned empty but general Android items exist
            if (games.isEmpty() && newest.isNotEmpty()) {
                games = newest.filter {
                    it.category?.slug?.contains("game", ignoreCase = true) == true ||
                        it.category?.name?.contains("game", ignoreCase = true) == true ||
                        it.category?.name?.contains("juego", ignoreCase = true) == true
                }.ifEmpty { newest.take(6) }
            }
            if (apps.isEmpty() && topRated.isNotEmpty()) {
                apps = topRated.filterNot { item -> games.any { it.id == item.id } }.ifEmpty { topRated.take(6) }
            }

            val receivedFreshData = games.isNotEmpty() || apps.isNotEmpty() || newest.isNotEmpty()
            val firstError = listOfNotNull(
                gamesRes.exceptionOrNull()?.message,
                appsRes.exceptionOrNull()?.message,
                newestRes.exceptionOrNull()?.message
            ).firstOrNull()

            if (receivedFreshData) {
                val categories = categoriesRes.getOrDefault(_uiState.value.categories)
                val hero = (games.take(3) + apps.take(2) + newest.take(2)).distinctBy { it.id }.take(5)
                if (categorySlug == null) {
                    homeCacheStorage.save(categories, games, apps, newest, topRated)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isOfflineFallback = false,
                        error = null,
                        categories = categories,
                        featured = hero,
                        androidGames = games,
                        androidApps = apps,
                        newestApks = newest,
                        topRatedApks = topRated
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isOfflineFallback = hasExistingContent,
                        error = if (hasExistingContent) null else (firstError ?: "El servidor no respondió a tiempo")
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            repo: CatalogRepository,
            cacheStorage: HomeCacheStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(repo, cacheStorage) as T
        }
    }
}
