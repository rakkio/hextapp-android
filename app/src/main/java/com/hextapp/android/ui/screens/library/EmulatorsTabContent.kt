package com.hextapp.android.ui.screens.library

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hextapp.android.data.emulators.AndroidEmulatorStatus
import com.hextapp.android.data.emulators.LocalRomItem
import com.hextapp.android.data.emulators.RomLaunchResult
import com.hextapp.android.ui.components.formatBytes
import com.hextapp.android.ui.theme.HextCyan
import com.hextapp.android.ui.theme.HextPrimary

@Composable
fun RomsTabContent(
    roms: List<LocalRomItem>,
    onPlayRom: (LocalRomItem) -> Unit,
    onRefresh: () -> Unit,
    onGoToExplore: () -> Unit
) {
    if (roms.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = HextCyan
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "No hay ROMs descomprimidas todavía. Descarga un juego retro o coloca tus ROMs en Download/roms/<consola>/.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Escanear")
                }
                Button(onClick = onGoToExplore) {
                    Text("Explorar catálogo")
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(roms, key = { it.file.absolutePath }) { rom ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rom.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${rom.consoleSlug.uppercase()} • ${formatBytes(rom.sizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onPlayRom(rom) },
                        colors = ButtonDefaults.buttonColors(containerColor = HextPrimary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Jugar")
                    }
                }
            }
        }
    }
}

@Composable
fun EmulatorsTabContent(
    context: Context,
    emulators: List<AndroidEmulatorStatus>,
    onInstallEmulator: (Context, AndroidEmulatorStatus) -> Unit,
    onRefresh: () -> Unit
) {
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
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Emuladores compatibles",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Instala el emulador para bootear tus ROMs directo desde Hextapp.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onRefresh) {
                        Text("Actualizar")
                    }
                }
            }
        }

        items(emulators, key = { it.id }) { emu ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = emu.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = emu.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (emu.installed) {
                        OutlinedButton(onClick = {}, enabled = false) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Instalado")
                        }
                    } else if (emu.apkDownloadUrl.isNotBlank()) {
                        Button(
                            onClick = { onInstallEmulator(context, emu) },
                            colors = ButtonDefaults.buttonColors(containerColor = HextPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Instalar")
                        }
                    } else {
                        Text(
                            "Externo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RomLaunchFeedbackDialog(
    result: RomLaunchResult?,
    context: Context,
    onInstallEmulator: (Context, AndroidEmulatorStatus) -> Unit,
    onDismiss: () -> Unit
) {
    when (result) {
        is RomLaunchResult.EmulatorNotInstalled -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Falta ${result.emulator.name}") },
                text = {
                    Text("Para abrir este juego necesitas instalar ${result.emulator.name}. ¿Quieres descargarlo e instalarlo ahora?")
                },
                confirmButton = {
                    if (result.emulator.apkDownloadUrl.isNotBlank()) {
                        Button(onClick = {
                            onInstallEmulator(context, result.emulator)
                            onDismiss()
                        }) {
                            Text("Instalar ${result.emulator.name}")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            )
        }

        is RomLaunchResult.OpenedAppFallback -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("${result.emulatorName} abierto") },
                text = {
                    Text("${result.note ?: "Selecciona tu ROM manualmente."}\n\nRuta: ${result.romPath}")
                },
                confirmButton = {
                    Button(onClick = onDismiss) { Text("Entendido") }
                }
            )
        }

        is RomLaunchResult.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("No se pudo iniciar") },
                text = { Text(result.message) },
                confirmButton = {
                    Button(onClick = onDismiss) { Text("Cerrar") }
                }
            )
        }

        else -> Unit
    }
}
