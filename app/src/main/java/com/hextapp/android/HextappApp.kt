package com.hextapp.android

import android.app.Application
import com.hextapp.android.data.download.ApkDownloadManager
import com.hextapp.android.data.local.ThemePreferences
import com.hextapp.android.data.local.TokenStorage
import com.hextapp.android.data.network.ApolloClientProvider
import com.hextapp.android.data.repository.AuthRepository
import com.hextapp.android.data.repository.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val tokenStorage = TokenStorage(application)
    val themePreferences = ThemePreferences(application)
    private val apolloProvider = ApolloClientProvider(tokenStorage)

    val catalogRepository = CatalogRepository(apolloProvider.apolloClient)
    val authRepository = AuthRepository(apolloProvider.apolloClient, tokenStorage)
    val apkDownloadManager = ApkDownloadManager(application, apolloProvider.okHttpClient)
}

class HextappApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.authRepository.restoreSession()
        }
    }
}
