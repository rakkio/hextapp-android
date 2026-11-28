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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hextapp.android.data.download.DownloadState
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        "Descargas" to state.localApks.size,
        "ROMs" to state.localRoms.size,
        "Emuladores" to state.emulators.count { it.installed },
        "Bóveda" to state.vaultItems.size,
        "Favoritos" to state.favoriteItems.size,
        "Guardados" to state.bookmarkItems.size
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Column(modifier = Modifier.fillMaxSize()) {
        HextappTopBar(
            title = "Mi Biblioteca",
            subtitle = "Desliza entre descargas, bóveda y listas",
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSearchClick = onGoToExplore
        )

        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {}
        ) {
            tabs.forEachIndexed { index, (title, count) ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = if (count > 0) "$title  $count" else title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { selectedTab ->
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
                                        Text("Espacio usado • Motor Nexus NX6R", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${formatBytes(totalSizeBytes)} • Limpieza auto 24h tras instalar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                                    if (apk.archiveKind != ArchiveKind.GENERIC_ZIP) {
                                        IconButton(
                                            onClick = { viewModel.archiveToVault(apk.file) },
                                            enabled = !state.isArchivingToVault
                                        ) {
                                            Icon(
                                                Icons.Outlined.Inventory2,
                                                contentDescription = "Archivar en Bóveda NX6R",
                                                tint = HextSecondary
                                            )
                                        }
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
                RomsTabContent(
                    roms = state.localRoms,
                    onPlayRom = { viewModel.launchRom(it) },
                    onRefresh = { viewModel.refreshEmulatorsAndRoms() },
                    onGoToExplore = onGoToExplore
                )
            }

            2 -> {
                EmulatorsTabContent(
                    context = context,
                    emulators = state.emulators,
                    onInstallEmulator = { ctx, emu -> viewModel.installEmulator(ctx, emu) },
                    onRefresh = { viewModel.refreshEmulatorsAndRoms() }
                )
            }

            3 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.isArchivingToVault) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (state.vaultItems.isEmpty()) {
                        EmptyState(
                            message = "Tu Bóveda NX6R está vacía. Archiva APKs o XAPKs desde Descargas para ahorrar ~35% de espacio y reinstalarlos sin gastar datos.",
                            ctaLabel = "Ver Descargas",
                            onCta = { scope.launch { pagerState.animateScrollToPage(0) } }
                        )
                    } else {
                        val vaultBytes = state.vaultItems.sumOf { it.sizeBytes }
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text(
                                            "Bóveda Nexus NX6R",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${state.vaultItems.size} paquete(s) archivados • ${formatBytes(vaultBytes)}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            items(state.vaultItems, key = { it.file.absolutePath }) { item ->
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
                                            imageVector = Icons.Outlined.Inventory2,
                                            contentDescription = null,
                                            tint = HextSecondary
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name.replace('-', ' ').replace('_', ' ')
                                                    .replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatBytes(item.sizeBytes)} • Archivo .nx6r",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = { viewModel.restoreFromVault(context, item.file) },
                                            colors = ButtonDefaults.buttonColors(containerColor = HextPrimary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Restaurar")
                                        }
                                        IconButton(onClick = { viewModel.deleteFromVault(item.file) }) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = "Eliminar de Bóveda",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            4 -> {
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

            5 -> {
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

    RomLaunchFeedbackDialog(
        result = state.romLaunchResult,
        context = context,
        onInstallEmulator = { ctx, emu -> viewModel.installEmulator(ctx, emu) },
        onDismiss = { viewModel.dismissLaunchResult() }
    )
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
