package com.hextapp.android.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hextapp.android.data.local.SearchHistoryStorage
import com.hextapp.android.data.repository.CatalogRepository
import com.hextapp.android.graphql.GetCategoriesQuery
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.graphql.type.ItemSortBy
import com.hextapp.android.graphql.type.Platform
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AndroidScopeFilter(val label: String, val defaultCategorySlug: String?) {
    ALL("Todo Android", null),
    GAMES("Solo Juegos", "android-games"),
    APPS("Solo Apps", "android-apps")
}

data class ExploreUiState(
    val query: String = "",
    val scopeFilter: AndroidScopeFilter = AndroidScopeFilter.ALL,
    val selectedCategorySlug: String? = null,
    val sortBy: ItemSortBy = ItemSortBy.POPULARITY,
    val isGridView: Boolean = false,
    val categories: List<GetCategoriesQuery.Category> = emptyList(),
    val items: List<ItemSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
    val totalCount: Int = 0,
    val recentQueries: List<String> = emptyList(),
    val error: String? = null
)

class ExploreViewModel(
    private val catalogRepository: CatalogRepository,
    private val searchHistoryStorage: SearchHistoryStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            searchHistoryStorage.recentQueries.collect { queries ->
                _uiState.update { it.copy(recentQueries = queries) }
            }
        }
        viewModelScope.launch {
            catalogRepository.getCategories(Platform.ANDROID).onSuccess { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
        loadPage(page = 1, append = false)
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            loadPage(page = 1, append = false)
        }
    }

    fun onScopeFilterSelected(filter: AndroidScopeFilter) {
        _uiState.update { it.copy(scopeFilter = filter, selectedCategorySlug = null) }
        loadPage(page = 1, append = false)
    }

    fun onCategorySelected(slug: String?) {
        _uiState.update { it.copy(selectedCategorySlug = slug) }
        loadPage(page = 1, append = false)
    }

    fun onSortChanged(sortBy: ItemSortBy) {
        _uiState.update { it.copy(sortBy = sortBy) }
        loadPage(page = 1, append = false)
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasNextPage) return
        loadPage(page = state.currentPage + 1, append = true)
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryStorage.clear() }
    }

    fun retry() {
        loadPage(page = 1, append = false)
    }

    private fun loadPage(page: Int, append: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !append,
                    isLoadingMore = append,
                    error = null
                )
            }
            val s = _uiState.value
            if (!append && s.query.trim().length >= 2) {
                searchHistoryStorage.addQuery(s.query)
            }
            val effectiveCategory = s.selectedCategorySlug ?: s.scopeFilter.defaultCategorySlug
            val result = catalogRepository.getItems(
                page = page,
                limit = 20,
                platform = "ANDROID",
                contentType = "apk",
                categorySlug = effectiveCategory,
                search = s.query,
                sortBy = s.sortBy
            )
            result.onSuccess { paged ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        items = if (append) (it.items + paged.items).distinctBy { item -> item.id } else paged.items,
                        hasNextPage = paged.hasNextPage,
                        currentPage = page,
                        totalCount = paged.totalCount
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = err.message ?: "Error al buscar APKs"
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            repo: CatalogRepository,
            searchHistoryStorage: SearchHistoryStorage
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ExploreViewModel(repo, searchHistoryStorage) as T
        }
    }
}
