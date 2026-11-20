package com.hextapp.android.ui.screens.home

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hextapp.android.ui.components.AppCarouselCard
import com.hextapp.android.ui.components.AppListRow
import com.hextapp.android.ui.components.HeroShowcasePager
import com.hextapp.android.ui.components.HextappTopBar
import com.hextapp.android.ui.components.StoreShimmerSkeleton
import com.hextapp.android.ui.components.pressScale
import com.hextapp.android.ui.theme.HextPrimary
import com.hextapp.android.ui.theme.HextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAppClick: (String) -> Unit,
    onSeeAllGames: () -> Unit,
    onSeeAllExplore: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HextappTopBar(
            title = "Hextapp",
            subtitle = if (state.androidApps.isNotEmpty() || state.androidGames.isNotEmpty())
                "🔥 ${(state.androidApps.size + state.androidGames.size)} apps cargadas"
            else "Tienda Android • Juegos y Apps",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSearchClick = onSeeAllExplore
        )

        if (state.isLoading) {
            StoreShimmerSkeleton()
            return
        }

        if (state.error != null && state.androidGames.isEmpty() && state.androidApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: "Error de conexión",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadHome(isRefresh = false) }) {
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
            onRefresh = { viewModel.loadHome(isRefresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero Showcase
                if (state.featured.isNotEmpty()) {
                    item {
                        HeroShowcasePager(items = state.featured, onAppClick = onAppClick)
                    }
                }

                // Quick Actions Strip
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAccessTile(
                            icon = Icons.Outlined.SportsEsports,
                            title = "Juegos",
                            subtitle = "121K+ APKs",
                            onClick = onSeeAllGames,
                            modifier = Modifier.weight(1f)
                        )
                        QuickAccessTile(
                            icon = Icons.Outlined.Apps,
                            title = "Apps",
                            subtitle = "185K+ APKs",
                            onClick = { viewModel.selectCategory("android-apps") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickAccessTile(
                            icon = Icons.Outlined.Explore,
                            title = "Explorar",
                            subtitle = "Categorías",
                            onClick = onSeeAllExplore,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Category Filter Chips
                if (state.categories.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = state.selectedCategorySlug == null,
                                    onClick = { viewModel.selectCategory(null) },
                                    label = { Text("Todo") },
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = state.selectedCategorySlug == null,
                                        borderColor = MaterialTheme.colorScheme.outline
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = HextPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            items(state.categories, key = { it.id }) { cat ->
                                FilterChip(
                                    selected = state.selectedCategorySlug == cat.slug,
                                    onClick = { viewModel.selectCategory(cat.slug) },
                                    label = { Text(cat.name.replace(" (Android APKs)", "")) },
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = state.selectedCategorySlug == cat.slug,
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
                }

                // Juegos Android
                if (state.androidGames.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Juegos Android Populares", onSeeAll = onSeeAllGames)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.androidGames, key = { it.id }) { game ->
                                AppCarouselCard(item = game, onClick = { onAppClick(game.slug) })
                            }
                        }
                    }
                }

                // Apps Android
                if (state.androidApps.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Apps Android Esenciales", onSeeAll = onSeeAllExplore)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.androidApps, key = { it.id }) { app ->
                                AppCarouselCard(item = app, onClick = { onAppClick(app.slug) })
                            }
                        }
                    }
                }

                // Recien actualizados
                if (state.newestApks.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recién actualizados", onSeeAll = onSeeAllExplore)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.newestApks, key = { it.id }) { app ->
                                AppCarouselCard(item = app, onClick = { onAppClick(app.slug) })
                            }
                        }
                    }
                }

                // Top Valorados
                if (state.topRatedApks.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Top Valorados", onSeeAll = onSeeAllExplore)
                    }
                    itemsIndexed(state.topRatedApks, key = { _, it -> it.id }) { index, app ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            AppListRow(
                                item = app,
                                rank = index + 1,
                                onClick = { onAppClick(app.slug) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.pressScale(onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onSeeAll) {
            Text("Ver todo", color = HextSecondary)
        }
    }
}
