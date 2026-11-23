package com.hextapp.android.ui.screens.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hextapp.android.data.download.DownloadState
import com.hextapp.android.data.nexus.ArchiveKind
import com.hextapp.android.ui.components.AppListRow
import com.hextapp.android.ui.components.HextappTopBar
import com.hextapp.android.ui.components.formatBytes
import com.hextapp.android.ui.theme.HextPrimary
import com.hextapp.android.ui.theme.HextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAppClick: (String) -> Unit,
    onGoToProfile: () -> Unit,
    onGoToExplore: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Descargas (${state.localApks.size})", "Favoritos", "Guardados")

    Column(modifier = Modifier.fillMaxSize()) {
        HextappTopBar(
            title = "Mi Biblioteca",
            subtitle = "Tus APKs, favoritos y guardados",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSearchClick = onGoToExplore
        )

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                if (state.localApks.isEmpty() && state.activeDownloads.isEmpty()) {
                    EmptyState(
                        message = "Aún no has descargado ningún APK en este dispositivo.",
                        ctaLabel = "Explorar APKs",
                        onCta = onGoToExplore
                    )
                } else {
                    val totalSizeBytes = state.localApks.sumOf { it.sizeBytes }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.activeDownloads.isNotEmpty()) {
                            items(state.activeDownloads.entries.toList(), key = { "active_${it.key}" }) { (itemId, dlState) ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        val percent = (dlState as? DownloadState.Downloading)?.percent ?: 0
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (dlState is DownloadState.Resolving) "Preparando descarga..." else "Descargando APK ($percent%)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            TextButton(
                                                onClick = { viewModel.cancelDownload(itemId) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text("Cancelar", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        if (dlState is DownloadState.Downloading && percent > 0) {
                                            LinearProgressIndicator(
                                                progress = { percent / 100f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Espacio usado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatBytes(totalSizeBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = {
                                        state.localApks.forEach { viewModel.deleteApk(it.file) }
                                    }) {
                                        Text("Limpiar todo", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        items(state.localApks, key = { it.file.absolutePath }) { apk ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Android,
                                        contentDescription = null,
                                        tint = HextSecondary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = apk.name.replace('-', ' ').replace('_', ' ')
                                                .replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val kindLabel = when (apk.archiveKind) {
                                            ArchiveKind.NXS6_ARCHIVE -> "Archivo NXS6 (Nexus)"
                                            ArchiveKind.SPLIT_APKS -> "Paquete dividido (Nexus)"
                                            ArchiveKind.ZIP_WITH_SINGLE_APK -> "ZIP con APK"
                                            ArchiveKind.GENERIC_ZIP -> "Archivo ZIP"
                                            ArchiveKind.SINGLE_APK -> if (apk.isInstalled) "Instalada ✓ (se limpia en 24h)" else "APK listo para instalar"
                                        }
                                        Text(
                                            text = "${formatBytes(apk.sizeBytes)} • $kindLabel",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (apk.isInstalled) HextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.installApk(context, apk.file) },
                                        colors = ButtonDefaults.buttonColors(containerColor = HextPrimary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (apk.archiveKind == ArchiveKind.GENERIC_ZIP) "Extraer" else "Instalar")
                                    }
                                    IconButton(onClick = { viewModel.deleteApk(apk.file) }) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "Eliminar",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                if (state.session == null) {
                    LoginPrompt(onGoToProfile)
                } else if (state.favoriteItems.isEmpty()) {
                    EmptyState(
                        message = "No tienes APKs en favoritos todavía.",
                        ctaLabel = "Descubrir APKs",
                        onCta = onGoToExplore
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.favoriteItems, key = { it.id }) { app ->
                            AppListRow(item = app, onClick = { onAppClick(app.slug) })
                        }
                    }
                }
            }

            2 -> {
                if (state.session == null) {
                    LoginPrompt(onGoToProfile)
                } else if (state.bookmarkItems.isEmpty()) {
                    EmptyState(
                        message = "No tienes APKs guardados todavía.",
                        ctaLabel = "Explorar catálogo",
                        onCta = onGoToExplore
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.bookmarkItems, key = { it.id }) { app ->
                            AppListRow(item = app, onClick = { onAppClick(app.slug) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String, ctaLabel: String, onCta: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onCta) {
                Text(ctaLabel)
            }
        }
    }
}

@Composable
private fun LoginPrompt(onGoToProfile: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Inicia sesión para sincronizar tus favoritos y guardados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onGoToProfile) {
                Text("Ir a Perfil")
            }
        }
    }
}
