package dev.milkthe.qbitplan.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.plan.PlanScreen
import dev.milkthe.qbitplan.ui.search.DetailScreen
import dev.milkthe.qbitplan.ui.search.SearchScreen
import dev.milkthe.qbitplan.ui.settings.AboutScreen
import dev.milkthe.qbitplan.ui.settings.LicensesScreen
import dev.milkthe.qbitplan.ui.settings.SettingsScreen
import dev.milkthe.qbitplan.ui.substitutions.SubstitutionsScreen
import dev.milkthe.qbitplan.ui.theme.QbitTheme
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object PlanRoute
@Serializable data object SubstitutionsRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data object AboutRoute
@Serializable data object LicensesRoute

/** [kind] is a `DetailKind` name. */
@Serializable data class DetailRoute(val kind: String, val id: String)

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector)

private val TopLevels = listOf(
    TopLevel(PlanRoute, PlanRoute::class, R.string.tab_plan, Icons.Outlined.CalendarMonth),
    TopLevel(SubstitutionsRoute, SubstitutionsRoute::class, R.string.tab_substitutions, Icons.Outlined.SwapHoriz),
    TopLevel(SearchRoute, SearchRoute::class, R.string.tab_search, Icons.Outlined.Search),
    TopLevel(SettingsRoute, SettingsRoute::class, R.string.tab_settings, Icons.Outlined.Settings),
)

@Composable
fun MainNavigation() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination

    Scaffold(
        containerColor = QbitTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = QbitTheme.colors.content1) {
                TopLevels.forEach { item ->
                    val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = PlanRoute, modifier = Modifier.padding(padding)) {
            composable<PlanRoute> { PlanScreen() }
            composable<SubstitutionsRoute> { SubstitutionsScreen() }
            composable<SearchRoute> { SearchScreen(onOpen = { kind, id -> nav.navigate(DetailRoute(kind.name, id)) }) }
            composable<SettingsRoute> { SettingsScreen(onAbout = { nav.navigate(AboutRoute) }) }
            composable<DetailRoute> { DetailScreen(onBack = { nav.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }, onLicenses = { nav.navigate(LicensesRoute) }) }
            composable<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
