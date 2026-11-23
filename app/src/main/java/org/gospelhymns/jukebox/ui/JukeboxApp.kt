package org.gospelhymns.jukebox.ui

import android.net.Uri
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.gospelhymns.jukebox.MainViewModel
import org.gospelhymns.jukebox.ui.SettingsScreen

private object Routes {
    const val Player = "player"
    const val Categories = "categories"
    const val Search = "search"
    const val Authors = "authors"
    const val Author = "author/{name}"
    const val Settings = "settings"
    fun author(name: String) = "author/${Uri.encode(name)}"
}

@Composable
fun JukeboxApp(
    vm: MainViewModel,
    startScreen: String?,
    startAuthor: String?,
    startQuery: String,
) {
    val nav = rememberNavController()
    val state = vm.state.value
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(state.toast) {
        val toast = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(toast)
        vm.consumeToast()
    }
    LaunchedEffect(startScreen, startAuthor) {
        when (startScreen) {
            "categories" -> nav.navigate(Routes.Categories) { launchSingleTop = true }
            "search" -> nav.navigate(Routes.Search) { launchSingleTop = true }
            "authors" -> nav.navigate(Routes.Authors) { launchSingleTop = true }
            "author" -> if (!startAuthor.isNullOrBlank()) {
                nav.navigate(Routes.author(startAuthor)) { launchSingleTop = true }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavItem(Zh.player, route == Routes.Player, Icons.Filled.MusicNote) {
                    nav.navigate(Routes.Player) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                }
                NavItem(Zh.categories, route == Routes.Categories, Icons.AutoMirrored.Filled.MenuBook) {
                    nav.navigate(Routes.Categories) { launchSingleTop = true }
                }
                NavItem(Zh.search, route == Routes.Search, Icons.Filled.Search) {
                    nav.navigate(Routes.Search) { launchSingleTop = true }
                }
                NavItem(Zh.authors, route == Routes.Authors || route == Routes.Author, Icons.Filled.Person) {
                    nav.navigate(Routes.Authors) { launchSingleTop = true }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.Player, modifier = Modifier.padding(padding)) {
            composable(Routes.Player) {
                PlayerScreen(
                    vm,
                    vm.state.value,
                    onAuthor = { name -> nav.navigate(Routes.author(name)) },
                    onBackToCategory = {
                        vm.finishReturnToCategory()
                        nav.navigate(Routes.Categories) { launchSingleTop = true }
                    },
                    onOpenCategory = {
                        vm.showCategoryOfCurrentHymn()
                        nav.navigate(Routes.Categories) { launchSingleTop = true }
                    },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Categories) {
                CategoryScreen(vm) { number, index, offset ->
                    vm.openHymnFromCategory(number, index, offset)
                    nav.popBackStack(Routes.Player, inclusive = false)
                }
            }
            composable(Routes.Search) {
                SearchScreen(vm, startQuery) { number ->
                    vm.openNumber(number, autoplay = false)
                    nav.popBackStack(Routes.Player, inclusive = false)
                }
            }
            composable(Routes.Authors) {
                AuthorListScreen(vm) { name -> nav.navigate(Routes.author(name)) }
            }
            composable(Routes.Settings) {
                SettingsScreen(vm)
            }
            composable(Routes.Author) { entry ->
                val encoded = entry.arguments?.getString("name").orEmpty()
                val name = Uri.decode(encoded)
                AuthorDetailScreen(vm, name) { number ->
                    vm.openNumber(number, autoplay = false)
                    nav.popBackStack(Routes.Player, inclusive = false)
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}
