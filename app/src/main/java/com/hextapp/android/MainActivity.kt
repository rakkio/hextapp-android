package com.hextapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hextapp.android.data.update.UpdateUiState
import com.hextapp.android.ui.screens.detail.AppDetailScreen
import com.hextapp.android.ui.screens.detail.AppDetailViewModel
import com.hextapp.android.ui.screens.explore.ExploreScreen
import com.hextapp.android.ui.screens.explore.ExploreViewModel
import com.hextapp.android.ui.screens.games.GamesScreen
import com.hextapp.android.ui.screens.games.GamesViewModel
import com.hextapp.android.ui.screens.home.HomeScreen
import com.hextapp.android.ui.screens.home.HomeViewModel
import com.hextapp.android.ui.screens.library.LibraryScreen
import com.hextapp.android.ui.screens.library.LibraryViewModel
import com.hextapp.android.ui.screens.profile.ProfileScreen
import com.hextapp.android.ui.screens.profile.ProfileViewModel
import com.hextapp.android.ui.theme.HextappTheme
import kotlinx.coroutines.launch

private sealed class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    data object Home : BottomTab("home", "Inicio", Icons.Outlined.Home, Icons.Rounded.Home)
    data object Games : BottomTab("games", "Juegos", Icons.Outlined.SportsEsports, Icons.Rounded.SportsEsports)
    data object Explore : BottomTab("explore", "Explorar", Icons.Outlined.Explore, Icons.Rounded.Explore)
    data object Library : BottomTab("library", "Biblioteca", Icons.Outlined.CollectionsBookmark, Icons.Rounded.CollectionsBookmark)
    data object Profile : BottomTab("profile", "Perfil", Icons.Outlined.Person, Icons.Rounded.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HextappApp).container
        setContent {
            val isDarkTheme by container.themePreferences.isDarkTheme.collectAsState(initial = true)
            val scope = rememberCoroutineScope()
            val toggleTheme: () -> Unit = {
                scope.launch { container.themePreferences.setDarkTheme(!isDarkTheme) }
            }

            HextappTheme(isDarkTheme = isDarkTheme) {
                HextappRoot(
                    container = container,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = toggleTheme
                )
            }
        }
    }
}

@Composable
private fun HextappRoot(
    container: AppContainer,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateState by container.appUpdateManager.uiState.collectAsState()

    LaunchedEffect(Unit) {
        container.appUpdateManager.checkForUpdates(
            autoTrigger = true,
            activityContextForAutoInstall = context
        )
    }

    when (val uState = updateState) {
        is UpdateUiState.Available -> {
            AlertDialog(
                onDismissRequest = { container.appUpdateManager.dismissDialog() },
                title = { Text("Nueva versión disponible (v${uState.release.version})") },
                text = {
                    Column {
                        Text(uState.release.releaseNotes)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Puedes activar la actualización automática desde Perfil > Actualizaciones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            container.appUpdateManager.downloadAndInstall(uState.release, context)
                        }
                    }) {
                        Text("Descargar e instalar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        scope.launch { container.appUpdateManager.skipVersion(uState.release.version) }
                    }) {
                        Text("Omitir")
                    }
                }
            )
        }
        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Descargando v${uState.release.version}...") },
                text = {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("La instalación se abrirá al finalizar la descarga.")
                    }
                },
                confirmButton = {}
            )
        }
        is UpdateUiState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = { container.appUpdateManager.dismissDialog() },
                title = { Text("Actualización lista (v${uState.release.version})") },
                text = { Text("Pulsa Instalar para aplicar la nueva versión de Hextapp.") },
                confirmButton = {
                    Button(onClick = {
                        container.appUpdateManager.installDownloadedUpdate(context, uState.file)
                    }) {
                        Text("Instalar ahora")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { container.appUpdateManager.dismissDialog() }) {
                        Text("Después")
                    }
                }
            )
        }
        else -> Unit
    }

    val tabs = listOf(BottomTab.Home, BottomTab.Games, BottomTab.Explore, BottomTab.Library, BottomTab.Profile)
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomTab.Home.route) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container.catalogRepository))
                HomeScreen(
                    viewModel = vm,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onAppClick = { slug -> navController.navigate("detail/$slug") },
                    onSeeAllGames = { navController.navigate(BottomTab.Games.route) },
                    onSeeAllExplore = { navController.navigate(BottomTab.Explore.route) }
                )
            }
            composable(BottomTab.Games.route) {
                val vm: GamesViewModel = viewModel(factory = GamesViewModel.factory(container.catalogRepository))
                GamesScreen(
                    viewModel = vm,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onAppClick = { slug -> navController.navigate("detail/$slug") },
                    onSearchClick = { navController.navigate(BottomTab.Explore.route) }
                )
            }
            composable(BottomTab.Explore.route) {
                val vm: ExploreViewModel = viewModel(factory = ExploreViewModel.factory(container.catalogRepository))
                ExploreScreen(
                    viewModel = vm,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onAppClick = { slug -> navController.navigate("detail/$slug") }
                )
            }
            composable(BottomTab.Library.route) {
                val vm: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.factory(
                        container.authRepository,
                        container.catalogRepository,
                        container.apkDownloadManager
                    )
                )
                LibraryScreen(
                    viewModel = vm,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onAppClick = { slug -> navController.navigate("detail/$slug") },
                    onGoToProfile = { navController.navigate(BottomTab.Profile.route) },
                    onGoToExplore = { navController.navigate(BottomTab.Explore.route) }
                )
            }
            composable(BottomTab.Profile.route) {
                val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(container.authRepository))
                ProfileScreen(
                    viewModel = vm,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    appUpdateManager = container.appUpdateManager,
                    updatePreferences = container.updatePreferences
                )
            }
            composable(
                route = "detail/{slug}",
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { entry ->
                val slug = entry.arguments?.getString("slug").orEmpty()
                val vm: AppDetailViewModel = viewModel(
                    key = "detail_$slug",
                    factory = AppDetailViewModel.factory(
                        slug = slug,
                        catalogRepo = container.catalogRepository,
                        authRepo = container.authRepository,
                        downloadManager = container.apkDownloadManager
                    )
                )
                AppDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onAppClick = { nextSlug -> navController.navigate("detail/$nextSlug") }
                )
            }
        }
    }
}
