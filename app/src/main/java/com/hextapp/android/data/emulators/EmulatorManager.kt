package com.hextapp.android.data.emulators

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.hextapp.android.data.nexus.NexusEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class EmulatorRegistryRoot(
    val version: Int = 1,
    val emulators: Map<String, EmulatorEntry> = emptyMap(),
    val systems: Map<String, RetroSystemEntry> = emptyMap()
)

@Serializable
data class EmulatorEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val requiresBios: Boolean = false,
    val biosFiles: List<String> = emptyList(),
    val android: AndroidEmulatorConfig? = null
)

@Serializable
data class AndroidEmulatorConfig(
    val packageName: String = "",
    val apkDownloadUrl: String = "",
    val launchMode: String = "intent_view",
    val mimeType: String = "application/octet-stream",
    val note: String? = null
)

@Serializable
data class RetroSystemEntry(
    val name: String,
    val extensions: List<String> = emptyList(),
    val emulatorId: String = "",
    val retroarchCore: String? = null,
    val requiresBios: Boolean = false,
    val biosFiles: List<String> = emptyList()
)

data class AndroidEmulatorStatus(
    val id: String,
    val name: String,
    val description: String,
    val packageName: String,
    val apkDownloadUrl: String,
    val installed: Boolean,
    val launchMode: String,
    val note: String?
)

data class LocalRomItem(
    val file: File,
    val name: String,
    val consoleSlug: String,
    val sizeBytes: Long
)

sealed interface RomLaunchResult {
    data class Launched(val emulatorName: String) : RomLaunchResult
    data class EmulatorNotInstalled(val emulator: AndroidEmulatorStatus) : RomLaunchResult
    data class OpenedAppFallback(val emulatorName: String, val romPath: String, val note: String?) : RomLaunchResult
    data class Error(val message: String) : RomLaunchResult
}

class EmulatorManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val registry: EmulatorRegistryRoot by lazy {
        runCatching {
            val raw = context.assets.open("emulators-registry.json").bufferedReader().use { it.readText() }
            json.decodeFromString<EmulatorRegistryRoot>(raw)
        }.getOrElse { EmulatorRegistryRoot() }
    }

    fun getRomsRootDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "roms").apply { mkdirs() }
    }

    fun getEmulatorStatuses(): List<AndroidEmulatorStatus> {
        return registry.emulators.values.mapNotNull { entry ->
            val cfg = entry.android ?: return@mapNotNull null
            AndroidEmulatorStatus(
                id = entry.id,
                name = entry.name,
                description = entry.description,
                packageName = cfg.packageName,
                apkDownloadUrl = cfg.apkDownloadUrl,
                installed = isPackageInstalled(cfg.packageName),
                launchMode = cfg.launchMode,
                note = cfg.note
            )
        }.sortedBy { it.name }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Extracts a compressed `.nx6r` / `.nxs6` ROM archive using NexusEngine
     * into `roms/<consoleSlug>/<itemSlug>/` and returns the primary ROM file.
     */
    suspend fun extractRomArchive(
        archiveFile: File,
        consoleSlug: String,
        itemSlug: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDir = File(getRomsRootDir(), "$consoleSlug/$itemSlug").apply { mkdirs() }
            val pkg = NexusEngine.extractNxs6(archiveFile, targetDir)
            val extensions = registry.systems[consoleSlug]?.extensions.orEmpty()
            pickRomByExtension(
                pkg.outputDir.walkTopDown().filter { it.isFile }.toList(),
                extensions
            ) ?: error("No compatible ROM file found after Nexus extraction")
        }
    }

    fun listExtractedRoms(): List<LocalRomItem> {
        val root = getRomsRootDir()
        if (!root.exists()) return emptyList()
        val items = mutableListOf<LocalRomItem>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { consoleDir ->
            val consoleSlug = consoleDir.name
            val exts = registry.systems[consoleSlug]?.extensions.orEmpty()
            consoleDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val ext = ".${file.extension.lowercase()}"
                    if (exts.isEmpty() || exts.any { it.equals(ext, ignoreCase = true) }) {
                        items.add(
                            LocalRomItem(
                                file = file,
                                name = file.nameWithoutExtension,
                                consoleSlug = consoleSlug,
                                sizeBytes = file.length()
                            )
                        )
                    }
                }
        }
        return items.sortedBy { it.name.lowercase() }
    }

    /**
     * Launches a ROM file in the configured Android emulator via `Intent.ACTION_VIEW`
     * + FileProvider URI, or falls back to opening the emulator directly if SAF-only.
     */
    fun launchRom(romFile: File, consoleSlug: String): RomLaunchResult {
        val system = registry.systems[consoleSlug]
            ?: return RomLaunchResult.Error("Consola no registrada: $consoleSlug")
        val emuStatus = getEmulatorStatuses().find { it.id == system.emulatorId }
            ?: return RomLaunchResult.Error("Emulador no configurado para $consoleSlug")

        if (!emuStatus.installed) {
            return RomLaunchResult.EmulatorNotInstalled(emuStatus)
        }

        if (emuStatus.launchMode == "open_app_with_path") {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(emuStatus.packageName)
                ?: return RomLaunchResult.Error("No se pudo abrir ${emuStatus.name}")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return RomLaunchResult.OpenedAppFallback(
                emulatorName = emuStatus.name,
                romPath = romFile.absolutePath,
                note = emuStatus.note
            )
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                romFile
            )
            val mime = registry.emulators[emuStatus.id]?.android?.mimeType ?: "application/octet-stream"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                setPackage(emuStatus.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            RomLaunchResult.Launched(emuStatus.name)
        }.getOrElse {
            val fallback = context.packageManager.getLaunchIntentForPackage(emuStatus.packageName)
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                RomLaunchResult.OpenedAppFallback(
                    emulatorName = emuStatus.name,
                    romPath = romFile.absolutePath,
                    note = emuStatus.note ?: "Selecciona la ROM manualmente en ${emuStatus.name}."
                )
            } else {
                RomLaunchResult.Error("Error abriendo ${emuStatus.name}: ${it.message}")
            }
        }
    }

    companion object {
        fun pickRomByExtension(files: List<File>, extensions: List<String>): File? {
            for (ext in extensions) {
                val normalized = ext.trimStart('.').lowercase()
                val match = files.firstOrNull { it.extension.equals(normalized, ignoreCase = true) }
                if (match != null) return match
            }
            return files.firstOrNull()
        }
    }
}
