package com.hextapp.android.data.local

import android.content.Context
import com.hextapp.android.graphql.GetCategoriesQuery
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.graphql.type.ContentType
import com.hextapp.android.graphql.type.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedCategory(
    val id: String,
    val name: String,
    val slug: String,
    val icon: String? = null,
    val order: Int = 0
)

@Serializable
data class CachedItem(
    val id: String,
    val name: String,
    val slug: String,
    val shortDescription: String = "",
    val contentType: String = "apk",
    val platform: String = "ANDROID",
    val currentVersion: String = "1.0.0",
    val currentSize: String = "",
    val downloads: Int = 0,
    val rating: Double = 0.0,
    val reviewsCount: Int = 0,
    val isFeatured: Boolean = false,
    val isNewItem: Boolean = false,
    val isHot: Boolean = false,
    val isPopular: Boolean = false,
    val logoUrl: String? = null,
    val logoAlt: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categorySlug: String? = null,
    val developerName: String = "Hextapp"
)

@Serializable
data class CachedHomePayload(
    val categories: List<CachedCategory> = emptyList(),
    val androidGames: List<CachedItem> = emptyList(),
    val androidApps: List<CachedItem> = emptyList(),
    val newestApks: List<CachedItem> = emptyList(),
    val topRatedApks: List<CachedItem> = emptyList(),
    val savedAtMillis: Long = 0L
)

class HomeCacheStorage(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile: File get() = File(context.filesDir, "home_cache_v1.json")

    suspend fun load(): CachedHomePayload? = withContext(Dispatchers.IO) {
        runCatching {
            if (!cacheFile.exists()) return@runCatching null
            json.decodeFromString<CachedHomePayload>(cacheFile.readText())
        }.getOrNull()
    }

    suspend fun save(
        categories: List<GetCategoriesQuery.Category>,
        games: List<ItemSummary>,
        apps: List<ItemSummary>,
        newest: List<ItemSummary>,
        topRated: List<ItemSummary>
    ) = withContext(Dispatchers.IO) {
        if (games.isEmpty() && apps.isEmpty() && newest.isEmpty()) return@withContext
        runCatching {
            val payload = CachedHomePayload(
                categories = categories.map {
                    CachedCategory(
                        id = it.id,
                        name = it.name,
                        slug = it.slug,
                        icon = it.icon,
                        order = it.order
                    )
                },
                androidGames = games.map { it.toCached() },
                androidApps = apps.map { it.toCached() },
                newestApks = newest.map { it.toCached() },
                topRatedApks = topRated.map { it.toCached() },
                savedAtMillis = System.currentTimeMillis()
            )
            cacheFile.writeText(json.encodeToString(payload))
        }
    }

    private fun ItemSummary.toCached(): CachedItem = CachedItem(
        id = id,
        name = name,
        slug = slug,
        shortDescription = shortDescription.orEmpty(),
        contentType = contentType.rawValue,
        platform = platform.rawValue,
        currentVersion = currentVersion,
        currentSize = currentSize,
        downloads = downloads,
        rating = rating,
        reviewsCount = reviewsCount,
        isFeatured = isFeatured,
        isNewItem = isNewItem,
        isHot = isHot,
        isPopular = isPopular,
        logoUrl = logo?.url,
        logoAlt = logo?.alt,
        categoryId = category?.id,
        categoryName = category?.name,
        categorySlug = category?.slug,
        developerName = developer.name
    )

    fun CachedCategory.toGraphql(): GetCategoriesQuery.Category = GetCategoriesQuery.Category(
        id = id,
        name = name,
        slug = slug,
        icon = icon,
        order = order,
        platform = Platform.ANDROID,
        metadata = GetCategoriesQuery.Metadata(totalItems = 0)
    )

    fun CachedItem.toGraphql(): ItemSummary = ItemSummary(
        id = id,
        name = name,
        slug = slug,
        shortDescription = shortDescription,
        contentType = ContentType.safeValueOf(contentType),
        platform = Platform.safeValueOf(platform),
        currentVersion = currentVersion,
        currentSize = currentSize,
        downloads = downloads,
        rating = rating,
        reviewsCount = reviewsCount,
        isFeatured = isFeatured,
        isNewItem = isNewItem,
        isHot = isHot,
        isPopular = isPopular,
        logo = logoUrl?.let { ItemSummary.Logo(url = it, alt = logoAlt) },
        steam = null,
        category = if (categoryId != null && categoryName != null && categorySlug != null) {
            ItemSummary.Category(id = categoryId, name = categoryName, slug = categorySlug)
        } else null,
        developer = ItemSummary.Developer(name = developerName)
    )
}
