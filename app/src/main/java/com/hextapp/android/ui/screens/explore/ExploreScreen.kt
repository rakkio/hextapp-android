package com.hextapp.android.ui.screens.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hextapp.android.graphql.type.ItemSortBy
import com.hextapp.android.ui.components.AppGridCard
import com.hextapp.android.ui.components.AppListRow
import com.hextapp.android.ui.components.HextappTopBar
import com.hextapp.android.ui.components.ShimmerBox
import com.hextapp.android.ui.theme.HextPrimary
import com.hextapp.android.ui.theme.HextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAppClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val quickSearches = listOf("Minecraft", "GTA", "Spotify", "Roblox", "WhatsApp", "Netflix")

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (if (state.isGridView) state.items.size / 2 else state.items.size) - 3 && state.hasNextPage
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HextappTopBar(
            title = "Explorar",
            subtitle = "${state.totalCount} APKs de Android",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Buscar juegos o apps Android...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                IconButton(onClick = { showSortSheet = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Ordenar", tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // Quick Search Suggestions when search bar is empty
        if (state.query.isBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickSearches) { suggestion ->
                    AssistChip(
                        onClick = { viewModel.onQueryChange(suggestion) },
                        label = { Text(suggestion) },
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Scope filter pills (Todo Android / Solo Juegos / Solo Apps)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AndroidScopeFilter.entries) { filter ->
                FilterChip(
                    selected = state.scopeFilter == filter,
                    onClick = { viewModel.onScopeFilterSelected(filter) },
                    label = { Text(filter.label) },
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = state.scopeFilter == filter,
                        borderColor = MaterialTheme.colorScheme.outline
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategorySlug == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text("Todas las categorías") },
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
                        onClick = { viewModel.onCategorySelected(cat.slug) },
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

        Spacer(Modifier.height(6.dp))

        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(6) {
                        ShimmerBox(modifier = Modifier.fillMaxWidth().height(84.dp), cornerRadius = 16.dp)
                    }
                }
            }
            state.error != null && state.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = viewModel::retry) { Text("Reintentar") }
                    }
                }
            }
            state.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron APKs con esos filtros.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                AnimatedContent(targetState = state.isGridView, label = "exploreViewMode") { grid ->
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (grid) {
                            val pairs = state.items.chunked(2)
                            items(pairs) { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(state.items, key = { it.id }) { app ->
                                AppListRow(item = app, onClick = { onAppClick(app.slug) })
                            }
                        }

                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = HextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        val sortOptions = listOf(
            ItemSortBy.POPULARITY to "Más populares",
            ItemSortBy.NEWEST to "Más recientes",
            ItemSortBy.DOWNLOADS to "Más descargados",
            ItemSortBy.RATING to "Mejor valorados",
            ItemSortBy.NAME to "Nombre (A-Z)"
        )
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Ordenar por",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                sortOptions.forEach { (sortEnum, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            if (state.sortBy == sortEnum) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = HextSecondary)
                            }
                        },
                        modifier = Modifier.clickable {
                            viewModel.onSortChanged(sortEnum)
                            showSortSheet = false
                        }
                    )
                }
            }
        }
    }
}
