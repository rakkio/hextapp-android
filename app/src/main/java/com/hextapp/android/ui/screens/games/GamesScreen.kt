package com.hextapp.android.ui.screens.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hextapp.android.ui.components.AppCarouselCard
import com.hextapp.android.ui.components.AppGridCard
import com.hextapp.android.ui.components.AppListRow
import com.hextapp.android.ui.components.HeroShowcasePager
import com.hextapp.android.ui.components.HextappTopBar
import com.hextapp.android.ui.components.StoreShimmerSkeleton
import com.hextapp.android.ui.theme.HextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAppClick: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HextappTopBar(
            title = "Juegos Android",
            subtitle = "APKs de juegos listos para instalar",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSearchClick = onSearchClick,
            extraAction = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    IconButton(onClick = viewModel::toggleViewMode, modifier = Modifier.size(38.dp)) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = "Cambiar vista",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        if (state.isLoading) {
            StoreShimmerSkeleton()
            return
        }

        if (state.error != null && state.games.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadGames(isRefresh = false) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reintentar")
                    }
                }
            }
            return
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.loadGames(isRefresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Genre Chips
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(AndroidGameGenre.entries) { genre ->
                            FilterChip(
                                selected = state.selectedGenre == genre,
                                onClick = { viewModel.selectGenre(genre) },
                                label = { Text(genre.label) },
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = state.selectedGenre == genre,
                                    borderColor = MaterialTheme.colorScheme.outline
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HextPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                if (state.featuredGames.isNotEmpty()) {
                    item {
                        HeroShowcasePager(items = state.featuredGames, onAppClick = onAppClick)
                    }
                }

                if (state.newestGames.isNotEmpty()) {
                    item {
                        Text(
                            text = "Nuevos en Android",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.newestGames, key = { it.id }) { game ->
                                AppCarouselCard(item = game, onClick = { onAppClick(game.slug) })
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Catálogo de Juegos (${state.selectedGenre.label})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item {
                    AnimatedContent(targetState = state.isGridView, label = "gamesViewMode") { grid ->
                        if (grid) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                state.games.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        AppGridCard(
                                            item = pair[0],
                                            onClick = { onAppClick(pair[0].slug) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (pair.size > 1) {
                                            AppGridCard(
                                                item = pair[1],
                                                onClick = { onAppClick(pair[1].slug) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                state.games.forEachIndexed { index, game ->
                                    AppListRow(
                                        item = game,
                                        rank = index + 1,
                                        onClick = { onAppClick(game.slug) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
