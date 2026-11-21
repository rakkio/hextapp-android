package com.hextapp.android.data.repository

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.hextapp.android.data.local.TokenStorage
import com.hextapp.android.graphql.AddBookmarkMutation
import com.hextapp.android.graphql.AddFavoriteMutation
import com.hextapp.android.graphql.ChangePasswordMutation
import com.hextapp.android.graphql.GetMeQuery
import com.hextapp.android.graphql.LoginMutation
import com.hextapp.android.graphql.RegisterDownloadMutation
import com.hextapp.android.graphql.RegisterMutation
import com.hextapp.android.graphql.RemoveBookmarkMutation
import com.hextapp.android.graphql.RemoveFavoriteMutation
import com.hextapp.android.graphql.UpdateProfileMutation
import com.hextapp.android.graphql.type.LoginInput
import com.hextapp.android.graphql.type.RegisterInput
import com.hextapp.android.graphql.type.UpdateProfileInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSession(
    val id: String,
    val name: String,
    val email: String,
    val username: String?,
    val avatar: String?,
    val bio: String?,
    val location: String?,
    val favorites: Set<String>,
    val bookmarks: Set<String>,
    val downloadsCount: Int
)

class AuthRepository(
    private val apolloClient: ApolloClient,
    private val tokenStorage: TokenStorage
) {
    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    suspend fun restoreSession() {
        val token = tokenStorage.getToken()
        if (token.isNullOrBlank()) {
            _session.value = null
            return
        }
        val response = runCatching { apolloClient.query(GetMeQuery()).execute() }.getOrNull()
        val user = response?.data?.user
        if (user != null) {
            _session.value = UserSession(
                id = user.id,
                name = user.name,
                email = user.email,
                username = user.username,
                avatar = user.avatar,
                bio = user.bio,
                location = user.location,
                favorites = user.favorites.toSet(),
                bookmarks = user.bookmarks.toSet(),
                downloadsCount = user.downloads.size
            )
        } else {
            tokenStorage.clearToken()
            _session.value = null
        }
    }

    suspend fun login(email: String, password: String): Result<UserSession> = runCatching {
        val response = apolloClient.mutation(LoginMutation(LoginInput(email = email, password = password))).execute()
        if (response.hasErrors()) {
            throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "Credenciales inválidas")
        }
        val payload = response.data?.login ?: throw IllegalStateException("Respuesta vacía")
        tokenStorage.saveToken(payload.token)
        val user = payload.user
        val userSession = UserSession(
            id = user.id,
            name = user.name,
            email = user.email,
            username = user.username,
            avatar = user.avatar,
            bio = null,
            location = null,
            favorites = user.favorites.toSet(),
            bookmarks = user.bookmarks.toSet(),
            downloadsCount = user.downloads.size
        )
        _session.value = userSession
        userSession
    }

    suspend fun register(name: String, username: String?, email: String, password: String): Result<UserSession> = runCatching {
        val response = apolloClient.mutation(
            RegisterMutation(
                RegisterInput(
                    name = name,
                    username = Optional.presentIfNotNull(username?.takeIf { it.isNotBlank() }),
                    email = email,
                    password = password
                )
            )
        ).execute()
        if (response.hasErrors()) {
            throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "No se pudo registrar")
        }
        val payload = response.data?.register ?: throw IllegalStateException("Respuesta vacía")
        tokenStorage.saveToken(payload.token)
        val user = payload.user
        val userSession = UserSession(
            id = user.id,
            name = user.name,
            email = user.email,
            username = user.username,
            avatar = user.avatar,
            bio = null,
            location = null,
            favorites = user.favorites.toSet(),
            bookmarks = user.bookmarks.toSet(),
            downloadsCount = user.downloads.size
        )
        _session.value = userSession
        userSession
    }

    suspend fun logout() {
        tokenStorage.clearToken()
        _session.value = null
    }

    suspend fun toggleFavorite(itemId: String): Result<Boolean> = runCatching {
        val current = _session.value ?: throw IllegalStateException("Inicia sesión para guardar favoritos")
        val isFav = current.favorites.contains(itemId)
        val ok = if (isFav) {
            apolloClient.mutation(RemoveFavoriteMutation(itemId)).execute().data?.removeFavorite ?: false
        } else {
            apolloClient.mutation(AddFavoriteMutation(itemId)).execute().data?.addFavorite ?: false
        }
        if (ok) {
            val updated = if (isFav) current.favorites - itemId else current.favorites + itemId
            _session.value = current.copy(favorites = updated)
        }
        !isFav
    }

    suspend fun toggleBookmark(itemId: String): Result<Boolean> = runCatching {
        val current = _session.value ?: throw IllegalStateException("Inicia sesión para guardar en biblioteca")
        val isBookmarked = current.bookmarks.contains(itemId)
        val ok = if (isBookmarked) {
            apolloClient.mutation(RemoveBookmarkMutation(itemId)).execute().data?.removeBookmark ?: false
        } else {
            apolloClient.mutation(AddBookmarkMutation(itemId)).execute().data?.addBookmark ?: false
        }
        if (ok) {
            val updated = if (isBookmarked) current.bookmarks - itemId else current.bookmarks + itemId
            _session.value = current.copy(bookmarks = updated)
        }
        !isBookmarked
    }

    suspend fun registerDownload(itemId: String) {
        if (_session.value == null) return
        runCatching {
            apolloClient.mutation(RegisterDownloadMutation(itemId)).execute()
        }
    }

    suspend fun updateProfile(name: String, bio: String, location: String): Result<UserSession> = runCatching {
        val response = apolloClient.mutation(
            UpdateProfileMutation(
                UpdateProfileInput(
                    name = Optional.present(name),
                    bio = Optional.present(bio),
                    location = Optional.present(location),
                )
            )
        ).execute()
        if (response.hasErrors()) throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "Error al actualizar")
        val user = response.data?.updateProfile ?: throw IllegalStateException("Respuesta vacía")
        val current = _session.value ?: throw IllegalStateException("No hay sesión activa")
        val updated = current.copy(
            name = user.name,
            bio = user.bio,
            location = user.location,
            avatar = user.avatar,
        )
        _session.value = updated
        updated
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Boolean> = runCatching {
        val response = apolloClient.mutation(
            ChangePasswordMutation(currentPassword = currentPassword, newPassword = newPassword)
        ).execute()
        if (response.hasErrors()) throw IllegalStateException(response.errors?.firstOrNull()?.message ?: "Error al cambiar contraseña")
        response.data?.changePassword?.success ?: false
    }
}
