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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.ui.theme.HextPrimary
import com.hextapp.android.ui.theme.HextSecondary
import java.util.Locale
import kotlin.math.PI
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
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(contentAlignment = Alignment.Center) {
                    HextappHexLogo()
                    Text(
                        text = "H",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (extraAction != null) {
                    extraAction()
                }
                if (onSearchClick != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        IconButton(onClick = onSearchClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Buscar",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
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

@Composable
fun HeroShowcasePager(
    items: List<ItemSummary>,
    onAppClick: (String) -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp
        ) { page ->
            val item = items[page]
            val isGame = item.category?.slug?.contains("game", ignoreCase = true) == true
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScale { onAppClick(item.slug) },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    HextPrimary.copy(alpha = 0.18f),
                                    HextSecondary.copy(alpha = 0.12f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(
                            url = item.logo?.url,
                            contentDescription = item.name,
                            size = 76.dp,
                            cornerRadius = 18.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (isGame) "JUEGO ANDROID" else "DESTACADO APK",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.shortDescription ?: item.category?.name ?: item.developer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(15.dp))
                                Text(String.format(Locale.US, "%.1f", item.rating), style = MaterialTheme.typography.labelMedium)
                                Text("• v${item.currentVersion} • ${item.currentSize}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(items.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (selected) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(165.dp), cornerRadius = 22.dp)
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
