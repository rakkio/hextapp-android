package com.hextapp.android.data.network

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.hextapp.android.BuildConfig
import com.hextapp.android.data.local.TokenStorage
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ApolloClientProvider(private val tokenStorage: TokenStorage) {

    private val apiHost: String? = BuildConfig.API_BASE_URL.toHttpUrlOrNull()?.host

    val publicOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val okHttpClient: OkHttpClient by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val requestBuilder = request.newBuilder()
            if (apiHost == null || request.url.host.equals(apiHost, ignoreCase = true)) {
                val token = runBlocking { tokenStorage.getToken() }
                if (!token.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
            }
            chain.proceed(requestBuilder.build())
        }

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apolloClient: ApolloClient by lazy {
        ApolloClient.Builder()
            .serverUrl(BuildConfig.GRAPHQL_URL)
            .okHttpClient(okHttpClient)
            .build()
    }
}
