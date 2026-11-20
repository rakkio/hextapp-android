package com.hextapp.android.ui.screens.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hextapp.android.data.repository.CatalogRepository
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.graphql.type.ItemSortBy
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AndroidGameGenre(val label: String, val slug: String) {
    ALL("Todos", "android-games"),
    ACTION("Acción", "action-android-apks"),
    ADVENTURE("Aventura", "adventure-android-apks"),
    ARCADE("Arcade", "arcade-android-apks"),
    RACING("Carreras", "racing-android-apks")
}

data class GamesUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedGenre: AndroidGameGenre = AndroidGameGenre.ALL,
    val isGridView: Boolean = true,
    val featuredGames: List<ItemSummary> = emptyList(),
    val games: List<ItemSummary> = emptyList(),
    val newestGames: List<ItemSummary> = emptyList()
)

class GamesViewModel(private val catalogRepository: CatalogRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    init {
        loadGames(isRefresh = false)
    }

    fun selectGenre(genre: AndroidGameGenre) {
        _uiState.update { it.copy(selectedGenre = genre) }
        loadGames(isRefresh = true)
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun loadGames(isRefresh: Boolean = true) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && it.games.isEmpty(),
                    isRefreshing = isRefresh,
                    error = null
                )
            }

            val genreSlug = _uiState.value.selectedGenre.slug
            val popularDef = async {
                catalogRepository.getItems(
                    page = 1,
                    limit = 24,
                    platform = "ANDROID",
                    contentType = "apk",
                    categorySlug = genreSlug,
                    sortBy = ItemSortBy.POPULARITY
                )
            }
            val newestDef = async {
                catalogRepository.getItems(
                    page = 1,
                    limit = 12,
                    platform = "ANDROID",
                    contentType = "apk",
                    categorySlug = genreSlug,
                    sortBy = ItemSortBy.NEWEST
                )
            }

            val popRes = popularDef.await()
            val newRes = newestDef.await()

            val popGames = popRes.getOrNull()?.items.orEmpty()
            val newGames = newRes.getOrNull()?.items.orEmpty()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (popGames.isEmpty() && newGames.isEmpty()) popRes.exceptionOrNull()?.message else null,
                    featuredGames = popGames.take(4),
                    games = popGames,
                    newestGames = newGames
                )
            }
        }
    }

    companion object {
        fun factory(repo: CatalogRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GamesViewModel(repo) as T
        }
    }
}
