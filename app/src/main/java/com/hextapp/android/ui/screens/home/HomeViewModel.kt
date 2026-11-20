package com.hextapp.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val error: String? = null,
    val categories: List<GetCategoriesQuery.Category> = emptyList(),
    val selectedCategorySlug: String? = null,
    val featured: List<ItemSummary> = emptyList(),
    val androidGames: List<ItemSummary> = emptyList(),
    val androidApps: List<ItemSummary> = emptyList(),
    val newestApks: List<ItemSummary> = emptyList(),
    val topRatedApks: List<ItemSummary> = emptyList()
)

class HomeViewModel(private val catalogRepository: CatalogRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHome(isRefresh = false)
    }

    fun selectCategory(slug: String?) {
        _uiState.update { it.copy(selectedCategorySlug = slug) }
        loadHome(isRefresh = true)
    }

    fun loadHome(isRefresh: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && it.androidGames.isEmpty() && it.androidApps.isEmpty(),
                    isRefreshing = isRefresh,
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

            val games = gamesRes.getOrNull()?.items.orEmpty()
            val apps = appsRes.getOrNull()?.items.orEmpty()
            val newest = newestRes.getOrNull()?.items.orEmpty()
            val topRated = topRatedRes.getOrNull()?.items.orEmpty()

            val hero = (games.take(3) + apps.take(2)).distinctBy { it.id }

            val firstError = listOfNotNull(
                gamesRes.exceptionOrNull()?.message,
                appsRes.exceptionOrNull()?.message
            ).firstOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (games.isEmpty() && apps.isEmpty()) firstError else null,
                    categories = categoriesRes.getOrDefault(it.categories),
                    featured = hero,
                    androidGames = games,
                    androidApps = apps,
                    newestApks = newest,
                    topRatedApks = topRated
                )
            }
        }
    }

    companion object {
        fun factory(repo: CatalogRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
        }
    }
}
