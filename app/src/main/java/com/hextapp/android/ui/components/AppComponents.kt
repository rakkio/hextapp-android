package com.hextapp.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp as lerpF
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage
import com.hextapp.android.R
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.ui.theme.HextCyan
import com.hextapp.android.ui.theme.HextPrimary
import com.hextapp.android.ui.theme.HextSecondary
import java.util.Locale
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun HextappHexLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(34.dp)) {
        val radius = size.minDimension / 2f - 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (i in 0 until 6) {
            val angle = (60 * i - 30) * PI / 180.0
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path = path,
            brush = Brush.linearGradient(listOf(HextPrimary, HextSecondary)),
            alpha = 0.22f
        )
        drawPath(
            path = path,
            brush = Brush.linearGradient(listOf(HextPrimary, HextSecondary)),
            style = Stroke(width = 2.4.dp.toPx())
        )
    }
}

@Composable
fun HextappTopBar(
    title: String,
    subtitle: String? = null,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSearchClick: (() -> Unit)? = null,
    extraAction: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.2.dp,
                        Brush.linearGradient(listOf(HextPrimary.copy(alpha = 0.8f), HextCyan.copy(alpha = 0.6f)))
                    )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hextapp_logo),
                        contentDescription = "Hextapp",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = HextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (extraAction != null) {
                    extraAction()
                }
                if (onSearchClick != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, HextPrimary.copy(alpha = 0.35f))
                    ) {
                        IconButton(onClick = onSearchClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Buscar",
                                tint = HextCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    IconButton(onClick = onToggleTheme, modifier = Modifier.size(38.dp)) {
                        AnimatedContent(targetState = isDarkTheme, label = "themeIcon") { dark ->
                            Icon(
                                imageVector = if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = if (dark) "Modo claro" else "Modo oscuro",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            HextPrimary.copy(alpha = 0.65f),
                            HextCyan.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun AppIconImage(
    url: String?,
    contentDescription: String?,
    size: Dp = 64.dp,
    cornerRadius: Dp = 16.dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(RoundedCornerShape(cornerRadius))
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(HextPrimary.copy(alpha = 0.22f), HextSecondary.copy(alpha = 0.18f)))
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Android,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(size * 0.52f)
                )
            }
        }
    }
}

private val HeroPalettes = listOf(
    listOf(Color(0xFF2A1468), Color(0xFF12182E), Color(0xFF0B3A4A)),
    listOf(Color(0xFF3A1248), Color(0xFF1A1230), Color(0xFF12305A)),
    listOf(Color(0xFF0E3A32), Color(0xFF101828), Color(0xFF1A2458)),
    listOf(Color(0xFF3A2410), Color(0xFF1A1428), Color(0xFF2A1848)),
    listOf(Color(0xFF142848), Color(0xFF10141F), Color(0xFF2A1860))
)

@Composable
fun HeroShowcasePager(
    items: List<ItemSummary>,
    onAppClick: (String) -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    var autoplayProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pagerState.settledPage, dragged, items.size) {
        autoplayProgress = 0f
        if (dragged || items.size < 2) return@LaunchedEffect
        val ticks = 48
        repeat(ticks) { step ->
            delay(100)
            if (pagerState.isScrollInProgress) return@LaunchedEffect
            autoplayProgress = (step + 1) / ticks.toFloat()
        }
        val next = (pagerState.settledPage + 1) % items.size
        pagerState.animateScrollToPage(next)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 14.dp,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp)
        ) { page ->
            val item = items[page]
            val distance = pageDistance(pagerState, page)
            val focus = (1f - distance).coerceIn(0f, 1f)
            HeroShowcaseCard(
                item = item,
                paletteIndex = page,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = lerpF(0.9f, 1f, focus)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerpF(0.55f, 1f, focus)
                    },
                onClick = { onAppClick(item.slug) }
            )
        }

        if (items.size > 1) {
            Spacer(Modifier.height(12.dp))
            HeroPageIndicator(
                pagerState = pagerState,
                pageCount = items.size,
                autoplayProgress = if (dragged || pagerState.isScrollInProgress) 0f else autoplayProgress
            )
        }
    }
}

@Composable
private fun HeroShowcaseCard(
    item: ItemSummary,
    paletteIndex: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isGame = item.category?.slug?.contains("game", ignoreCase = true) == true ||
        item.category?.name?.contains("juego", ignoreCase = true) == true
    val badge = when {
        item.isHot -> "HOT"
        item.isNewItem -> "NUEVO"
        isGame -> "JUEGO"
        else -> "DESTACADO"
    }
    val banner = item.steam?.hero?.takeIf { it.isNotBlank() }
        ?: item.steam?.header?.takeIf { it.isNotBlank() }
        ?: item.steam?.capsule?.takeIf { it.isNotBlank() }
    val palette = HeroPalettes[paletteIndex % HeroPalettes.size]

    Surface(
        modifier = modifier.pressScale(onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.28f),
                    HextPrimary.copy(alpha = 0.35f)
                )
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (banner != null) {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(palette))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.25f),
                            1f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(
                        url = item.logo?.url,
                        contentDescription = item.name,
                        size = 52.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.shortDescription ?: item.developer.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", item.rating),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = "• ${formatDownloads(item.downloads)} • ${item.currentSize}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPageIndicator(
    pagerState: PagerState,
    pageCount: Int,
    autoplayProgress: Float
) {
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val amount = (1f - (position - index).absoluteValue).coerceIn(0f, 1f)
            val width = lerpDp(6.dp, 28.dp, amount)
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.9f))
            ) {
                if (amount > 0.85f && autoplayProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(autoplayProgress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else if (amount > 0.05f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = amount))
                    )
                }
            }
        }
    }
}

private fun pageDistance(pagerState: PagerState, page: Int): Float {
    return ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
}

@Composable
fun ScreenshotCarousel(
    urls: List<String>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (urls.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { urls.size })
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(158.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 2,
            modifier = Modifier
                .fillMaxWidth()
                .height(286.dp)
        ) { page ->
            val focus = (1f - pageDistance(pagerState, page)).coerceIn(0f, 1f)
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = lerpF(0.94f, 1f, focus)
                        scaleX = scale
                        scaleY = scale
                    }
                    .pressScale { onOpen(page) },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = lerpF(0.4f, 1f, focus))
                )
            ) {
                AsyncImage(
                    model = urls[page],
                    contentDescription = "Captura ${page + 1} de ${urls.size}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (urls.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${pagerState.currentPage + 1} / ${urls.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp)
            )
        }
    }
}

@Composable
fun FullscreenScreenshotViewer(
    urls: List<String>,
    initialPage: Int,
    onDismiss: () -> Unit
) {
    if (urls.isEmpty()) return
    val start = initialPage.coerceIn(0, urls.lastIndex)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(initialPage = start) { urls.size }
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val focus = (1f - pageDistance(pagerState, page)).coerceIn(0f, 1f)
                    AsyncImage(
                        model = urls[page],
                        contentDescription = "Captura ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = lerpF(0.35f, 1f, focus) }
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}  /  ${urls.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppCarouselCard(
    item: ItemSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(142.dp)
            .pressScale(onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    HextPrimary.copy(alpha = 0.32f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppIconImage(
                url = item.logo?.url,
                contentDescription = item.name,
                size = 74.dp,
                cornerRadius = 18.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.category?.name ?: item.developer.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = String.format(Locale.US, "%.1f", item.rating),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "• ${item.currentSize}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AppGridCard(
    item: ItemSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.22f),
                    HextPrimary.copy(alpha = 0.32f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                AppIconImage(
                    url = item.logo?.url,
                    contentDescription = item.name,
                    size = 58.dp,
                    cornerRadius = 14.dp
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(String.format(Locale.US, "%.1f", item.rating), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.category?.name ?: item.developer.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "v${item.currentVersion} • ${item.currentSize}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AppListRow(
    item: ItemSummary,
    rank: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(28.dp)
                )
            }
            AppIconImage(
                url = item.logo?.url,
                contentDescription = item.name,
                size = 56.dp,
                cornerRadius = 14.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.developer.name} • v${item.currentVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", item.rating),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatDownloads(item.downloads),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = item.currentSize,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RatingBar(
    rating: Float,
    maxRating: Float = 5f,
    totalReviews: Int = 0,
    modifier: Modifier = Modifier
) {
    val filled = (rating / maxRating).coerceIn(0f, 1f)
    val starColor = Color(0xFFFFB300)
    val emptyColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { index ->
                val starFill = when {
                    index < rating.toInt() -> 1f
                    index < rating -> rating - index.toFloat()
                    else -> 0f
                }
                Box(modifier = Modifier.size(20.dp)) {
                    // Empty star background
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = emptyColor,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Filled star foreground (clipped by fill fraction)
                    if (starFill > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(FractionalClipShape(starFill))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = starColor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (totalReviews > 0) {
                Text(
                    text = "(${formatDownloads(totalReviews)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Rating distribution bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(emptyColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(filled)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(starColor, Color(0xFFFF8F00)))
                    )
            )
        }
    }
}

private class FractionalClipShape(private val fraction: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ) = androidx.compose.ui.graphics.Outline.Rectangle(
        androidx.compose.ui.geometry.Rect(0f, 0f, size.width * fraction, size.height)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.outline
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 300f)
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun StoreShimmerSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(232.dp), cornerRadius = 28.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                ShimmerBox(modifier = Modifier.width(126.dp).height(165.dp), cornerRadius = 18.dp)
            }
        }
        repeat(4) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(84.dp), cornerRadius = 16.dp)
        }
    }
}

fun formatDownloads(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
