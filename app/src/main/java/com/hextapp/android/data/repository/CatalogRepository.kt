package com.hextapp.android.data.repository

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.hextapp.android.graphql.GetCategoriesQuery
import com.hextapp.android.graphql.GetItemDetailQuery
import com.hextapp.android.graphql.GetItemReviewsQuery
import com.hextapp.android.graphql.GetItemsQuery
import com.hextapp.android.graphql.GetSimilarItemsQuery
import com.hextapp.android.graphql.fragment.ItemSummary
import com.hextapp.android.graphql.type.ItemSortBy
import com.hextapp.android.graphql.type.PaginationInput
import com.hextapp.android.graphql.type.Platform

data class PagedItems(
    val items: List<ItemSummary>,
    val hasNextPage: Boolean,
    val totalCount: Int
)

class CatalogRepository(private val apolloClient: ApolloClient) {

    suspend fun getCategories(platform: Platform? = null): Result<List<GetCategoriesQuery.Category>> = runCatching {
        val response = apolloClient.query(
            GetCategoriesQuery(platform = Optional.presentIfNotNull(platform))
        ).execute()
        if (response.hasErrors()) {
            throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "Error cargando categorías")
        }
        response.data?.categories.orEmpty().sortedBy { it.order }
    }

    suspend fun getItems(
        page: Int = 1,
        limit: Int = 15,
        platform: String? = null,
        contentType: String? = null,
        categorySlug: String? = null,
        search: String? = null,
        sortBy: ItemSortBy = ItemSortBy.POPULARITY
    ): Result<PagedItems> = runCatching {
        val response = apolloClient.query(
            GetItemsQuery(
                page = Optional.present(page),
                limit = Optional.present(limit),
                platform = Optional.presentIfNotNull(platform),
                contentType = Optional.presentIfNotNull(contentType),
                categorySlug = Optional.presentIfNotNull(categorySlug),
                includeChildren = Optional.present(true),
                search = Optional.presentIfNotNull(search?.takeIf { it.isNotBlank() }),
                sortBy = Optional.present(sortBy)
            )
        ).execute()

        if (response.hasErrors()) {
            throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "Error cargando catálogo")
        }
        val result = response.data?.items
        PagedItems(
            items = result?.items?.map { it.itemSummary }.orEmpty(),
            hasNextPage = result?.pagination?.hasNextPage ?: false,
            totalCount = result?.pagination?.totalCount ?: 0
        )
    }

    suspend fun getItemDetail(slug: String): Result<GetItemDetailQuery.Item?> = runCatching {
        val response = apolloClient.query(GetItemDetailQuery(slug = Optional.present(slug))).execute()
        if (response.hasErrors()) {
            throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "Error cargando ficha")
        }
        response.data?.item
    }

    suspend fun getSimilarItems(
        contentType: String,
        categoryId: String?,
        excludeId: String
    ): Result<List<ItemSummary>> = runCatching {
        val response = apolloClient.query(
            GetSimilarItemsQuery(
                contentType = contentType,
                categoryId = Optional.presentIfNotNull(categoryId),
                excludeId = excludeId,
                limit = Optional.present(10)
            )
        ).execute()
        response.data?.similarApps?.map { it.itemSummary }.orEmpty()
    }

    suspend fun getItemReviews(itemId: String): Result<GetItemReviewsQuery.Data?> = runCatching {
        val response = apolloClient.query(
            GetItemReviewsQuery(
                itemId = itemId,
                pagination = Optional.present(PaginationInput(page = Optional.present(1), limit = Optional.present(15)))
            )
        ).execute()
        response.data
    }
}
