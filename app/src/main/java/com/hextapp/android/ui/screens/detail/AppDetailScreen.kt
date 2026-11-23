package com.hextapp.android.ui.screens.detail

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.hextapp.android.data.download.DownloadState
import com.hextapp.android.ui.components.AppCarouselCard
import com.hextapp.android.ui.components.AppIconImage
import com.hextapp.android.ui.components.RatingBar
import com.hextapp.android.ui.components.WebDownloadDialog
import com.hextapp.android.ui.components.formatBytes
import com.hextapp.android.ui.components.formatDownloads
import com.hextapp.android.ui.theme.HextPrimary
import com.hextapp.android.ui.theme.HextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    viewModel: AppDetailViewModel,
    onBack: () -> Unit,
    onAppClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedDescription by remember { mutableStateOf(false) }
    var fullscreenImageIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = state.item?.itemSummary?.name ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (state.isFavorite) Color(0xFFFF5C7A) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = viewModel::toggleBookmark) {
                            Icon(
                                imageVector = if (state.isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Guardar",
                                tint = if (state.isBookmarked) HextSecondary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        val itemSlug = state.item?.itemSummary?.slug
                        if (itemSlug != null) {
                            IconButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://hextapp.com/android-apks/$itemSlug")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir APK"))
                            }) {
                                Icon(Icons.Outlined.Share, contentDescription = "Compartir")
                            }
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HextPrimary)
                }
            }
            state.error != null || state.item == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "No disponible", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = viewModel::loadDetail) { Text("Reintentar") }
                    }
                }
            }
            else -> {
                val item = state.item!!
                val summary = item.itemSummary
                val screenshotUrls = item.images.mapNotNull { it.url }.filter { it.isNotBlank() }
                val latestVersion = item.versions.firstOrNull { it.isActive } ?: item.versions.firstOrNull()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header Play-Store Style
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIconImage(
                                    url = summary.logo?.url,
                                    contentDescription = summary.name,
                                    size = 84.dp,
                                    cornerRadius = 20.dp
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(summary.name, style = MaterialTheme.typography.headlineMedium)
                                    Text(
                                        text = summary.developer.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text(
                                            text = "${summary.category?.name?.replace(" (Android APKs)", "") ?: "Android APK"} • v${summary.currentVersion}",
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp, horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    RatingBar(
                                        rating = summary.rating.toFloat(),
                                        totalReviews = summary.reviewsCount,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        MetricColumn(
                                            top = formatDownloads(summary.downloads),
                                            bottom = "Descargas"
                                        )
                                        MetricColumn(
                                            top = summary.currentSize,
                                            bottom = "Tamaño APK"
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            DownloadActionButton(
                                state = state.downloadState,
                                onClick = { viewModel.onDownloadOrInstall(context) },
                                onCancel = viewModel::cancelDownload,
                                onOpenInBrowser = { url ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            )
                        }
                    }

                    // Screenshots
                    if (screenshotUrls.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(screenshotUrls) { index, url ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "Captura ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .height(210.dp)
                                                .width(120.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { fullscreenImageIndex = index }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Description
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Acerca de esta app", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (expandedDescription) Int.MAX_VALUE else 5,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = { expandedDescription = !expandedDescription },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (expandedDescription) "Ver menos" else "Leer más", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }

                    // Changelog (Novedades)
                    val changelog = latestVersion?.changelog?.takeIf { it.isNotBlank() }
                    if (changelog != null) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Novedades de la v${latestVersion.version}", style = MaterialTheme.typography.titleMedium)
                                    Text(changelog, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Technical Specs
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Información técnica del APK", style = MaterialTheme.typography.titleMedium)
                                latestVersion?.metadata?.packageName?.let {
                                    SpecRow("Paquete", it)
                                }
                                latestVersion?.metadata?.minVersion?.let {
                                    SpecRow("Android mínimo", it)
                                }
                                SpecRow("Versión", summary.currentVersion)
                                SpecRow("Tamaño", summary.currentSize)
                                val archs = latestVersion?.metadata?.architecture?.joinToString(", ")
                                    ?: item.architecture.joinToString(", ")
                                if (archs.isNotBlank()) {
                                    SpecRow("Arquitectura", archs)
                                }
                            }
                        }
                    }

                    // Reviews
                    if (state.reviews.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text("Valoraciones y reseñas", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(10.dp))
                                state.reviews.take(5).forEach { review ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(review.user.name, style = MaterialTheme.typography.titleMedium)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("${review.rating}", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(review.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(review.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Similar Android APKs
                    if (state.similar.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "APKs similares",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.similar, key = { it.id }) { app ->
                                        AppCarouselCard(item = app, onClick = { onAppClick(app.slug) })
                                    }
                                }
                            }
                        }
                    }
                }

                // Fullscreen Screenshot Viewer
                val startIndex = fullscreenImageIndex
                if (startIndex != null && screenshotUrls.isNotEmpty()) {
                    Dialog(
                        onDismissRequest = { fullscreenImageIndex = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val pagerState = rememberPagerState(initialPage = startIndex) { screenshotUrls.size }
                                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                                    AsyncImage(
                                        model = screenshotUrls[page],
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                IconButton(
                                    onClick = { fullscreenImageIndex = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                                }
                            }
                        }
                    }
                }
                val browserState = state.downloadState as? DownloadState.RequiresBrowser
                if (browserState != null) {
                    WebDownloadDialog(
                        url = browserState.url,
                        itemName = browserState.itemName,
                        onDismiss = viewModel::dismissBrowserDownload,
                        onDownloadIntercepted = { downloadUrl, cookie, userAgent, referer ->
                            viewModel.onWebViewDownloadCaptured(context, downloadUrl, cookie, userAgent, referer)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    state: DownloadState,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onOpenInBrowser: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state is DownloadState.Completed) HextSecondary else HextPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                when (state) {
                    is DownloadState.Idle, is DownloadState.RequiresBrowser -> {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Descargar APK", style = MaterialTheme.typography.titleMedium)
                    }
                    is DownloadState.Resolving -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Preparando enlace...", style = MaterialTheme.typography.titleMedium)
                    }
                    is DownloadState.Downloading -> {
                        Text(
                            text = if (state.totalBytes > 0) {
                                "Descargando ${state.percent}% (${formatBytes(state.bytesRead)})"
                            } else {
                                "Descargando (${formatBytes(state.bytesRead)})"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    is DownloadState.Extracting -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Descomprimiendo ${state.percent}%...", style = MaterialTheme.typography.titleMedium)
                    }
                    is DownloadState.Installing -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Instalando paquete...", style = MaterialTheme.typography.titleMedium)
                    }
                    is DownloadState.Completed -> {
                        Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Instalar APK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    is DownloadState.Error -> {
                        Text("Reintentar descarga", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (state is DownloadState.Downloading || state is DownloadState.Resolving) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(52.dp)
                ) {
                    IconButton(onClick = onCancel, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancelar descarga")
                    }
                }
            }
        }

        if (state is DownloadState.Resolving ||
            state is DownloadState.Downloading ||
            state is DownloadState.Extracting ||
            state is DownloadState.Installing
        ) {
            Spacer(Modifier.height(10.dp))
            NexusPipelineStatusCard(state = state)
        }

        if (state is DownloadState.Error) {
            Spacer(Modifier.height(6.dp))
            Text(text = state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            if (!state.fallbackUrl.isNullOrBlank()) {
                TextButton(
                    onClick = { onOpenInBrowser(state.fallbackUrl) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Abrir enlace en navegador", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun NexusPipelineStatusCard(state: DownloadState) {
    val activeStep = when (state) {
        is DownloadState.Resolving -> 0
        is DownloadState.Downloading -> 0
        is DownloadState.Extracting -> 1
        is DownloadState.Installing -> 3
        is DownloadState.Completed -> 4
        else -> -1
    }
    val steps = listOf("Descarga", "Nexus NX6R", "SHA-256", "Instalar")

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Motor Nexus • NX6R / NXS6",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = HextSecondary
                )
                val pct = when (state) {
                    is DownloadState.Downloading -> state.percent
                    is DownloadState.Extracting -> state.percent
                    else -> 0
                }
                if (pct > 0) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (state is DownloadState.Downloading || state is DownloadState.Extracting) {
                val pct = when (state) {
                    is DownloadState.Downloading -> state.percent
                    is DownloadState.Extracting -> state.percent
                    else -> 0
                }
                if (pct > 0) {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = HextSecondary
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = HextSecondary)
                }
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = HextPrimary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                steps.forEachIndexed { idx, label ->
                    val done = idx < activeStep
                    val current = idx == activeStep
                    Text(
                        text = if (done) "✓ $label" else label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (current || done) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            done -> HextSecondary
                            current -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(top: String, bottom: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(top, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(bottom, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
