package dev.milkthe.qbitplan.ui.navigation

import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.milkthe.qbitplan.R
import dev.milkthe.qbitplan.ui.common.Hairline
import dev.milkthe.qbitplan.ui.onboarding.OnboardingScreen
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

/** Profile editor: the onboarding steps, opened on the last one. */
@Serializable data object ProfileRoute

/** [kind] is a `DetailKind` name. */
@Serializable data class DetailRoute(val kind: String, val id: String)

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector)

private val TopLevels = listOf(
    TopLevel(PlanRoute, PlanRoute::class, R.string.tab_plan, Icons.Outlined.CalendarMonth),
    TopLevel(SubstitutionsRoute, SubstitutionsRoute::class, R.string.tab_substitutions, Icons.Outlined.SwapHoriz),
    TopLevel(SearchRoute, SearchRoute::class, R.string.tab_search, Icons.Outlined.Search),
    TopLevel(SettingsRoute, SettingsRoute::class, R.string.tab_settings, Icons.Outlined.Settings),
)

private fun NavHostController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun MainNavigation(badgeVm: BadgeViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val badge by badgeVm.todayChanges.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = QbitTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // The profile editor is full screen with its own button at the bottom, like the onboarding.
            AnimatedVisibility(
                visible = destination?.hasRoute(ProfileRoute::class) != true,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column(Modifier.background(QbitTheme.colors.surface)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(QbitTheme.colors.border))
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp).padding(horizontal = 8.dp)) {
                        TopLevels.forEach { item ->
                            val selected = destination?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
                            NavItem(
                                item = item,
                                selected = selected,
                                badge = if (item.route == SubstitutionsRoute && badge > 0) badge else null,
                                onClick = { nav.openTab(item.route) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val openDetail = { kind: dev.milkthe.qbitplan.ui.search.DetailKind, id: String -> nav.navigate(DetailRoute(kind.name, id)) }
        val editProfile = { nav.navigate(ProfileRoute) { launchSingleTop = true } }
        NavHost(
            nav,
            startDestination = PlanRoute,
            modifier = Modifier.padding(padding),
            enterTransition = {
                if (isTabSwitch()) tabIn(towardsEnd = forward(initialState.destination, targetState.destination)) else pushIn()
            },
            exitTransition = {
                if (isTabSwitch()) tabOut(towardsEnd = forward(initialState.destination, targetState.destination)) else pushedAway()
            },
            popEnterTransition = { poppedBack() },
            popExitTransition = { popOut() },
            // The default shrinks the page; follow the finger like system apps instead.
            predictivePopEnterTransition = { edge -> poppedBack(fromRight = edge == BackEventCompat.EDGE_RIGHT) },
            predictivePopExitTransition = { edge -> popOut(towardsLeft = edge == BackEventCompat.EDGE_RIGHT) },
        ) {
            screen<PlanRoute> { PlanScreen(onEditProfile = editProfile, onOpenSettings = { nav.openTab(SettingsRoute) }) }
            screen<SubstitutionsRoute> { SubstitutionsScreen() }
            screen<SearchRoute> { SearchScreen(onOpen = openDetail) }
            screen<SettingsRoute> { SettingsScreen(onAbout = { nav.navigate(AboutRoute) }, onEditProfile = editProfile) }
            screen<DetailRoute> { DetailScreen(onBack = { nav.popBackStack() }, onOpen = openDetail) }
            screen<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }, onLicenses = { nav.navigate(LicensesRoute) }) }
            screen<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
            screen<ProfileRoute> { OnboardingScreen(onClose = { nav.popBackStack() }) }
        }
    }
}

/** Opaque, so a screen sliding over another one hides it. */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable () -> Unit) =
    composable<T> { Box(Modifier.fillMaxSize().background(QbitTheme.colors.background)) { content() } }

private fun NavDestination.tabIndex(): Int = TopLevels.indexOfFirst { hasRoute(it.routeClass) }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.tabIndex() >= 0 && targetState.destination.tabIndex() >= 0

/** Tabs slide by their position in the bar. */
private fun forward(from: NavDestination, to: NavDestination): Boolean = to.tabIndex() > from.tabIndex()

private val SlideSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)

private fun tabIn(towardsEnd: Boolean): EnterTransition =
    slideInHorizontally(SlideSpec) { w -> if (towardsEnd) w / 4 else -w / 4 } + fadeIn(tween(220, delayMillis = 60))

private fun tabOut(towardsEnd: Boolean): ExitTransition =
    slideOutHorizontally(SlideSpec) { w -> if (towardsEnd) -w / 4 else w / 4 } + fadeOut(tween(140))

// Screens opened on top (detail, about) slide over the previous one, which drifts behind them.
private fun pushIn(): EnterTransition = slideInHorizontally(SlideSpec) { w -> w }

private fun pushedAway(): ExitTransition = slideOutHorizontally(SlideSpec) { w -> -w / 4 }

private fun popOut(towardsLeft: Boolean = false): ExitTransition =
    slideOutHorizontally(SlideSpec) { w -> if (towardsLeft) -w else w }

private fun poppedBack(fromRight: Boolean = false): EnterTransition =
    slideInHorizontally(SlideSpec) { w -> if (fromRight) w / 4 else -w / 4 }

@Composable
private fun NavItem(item: TopLevel, selected: Boolean, badge: Int?, onClick: () -> Unit, modifier: Modifier) {
    val colors = QbitTheme.colors
    val pill by animateFloatAsState(if (selected) 1f else 0f, label = "navPill")
    Column(
        modifier = modifier.fillMaxHeight().clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(width = 64.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pill; scaleX = 0.4f + 0.6f * pill }
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent.solid.copy(alpha = 0.16f)),
            )
            Icon(item.icon, contentDescription = null, tint = if (selected) colors.accent.softForeground else colors.muted)
            badge?.let {
                Text(
                    "$it",
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onWarning,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = (-2).dp)
                        .widthIn(min = 18.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(colors.warning.solid)
                        .padding(horizontal = 5.dp),
                )
            }
        }
        Text(
            stringResource(item.label),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.foreground else colors.muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
