package com.chai.aiskin.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.chai.aiskin.core.designsystem.AiSkinTheme
import com.chai.aiskin.domain.model.AuthState
import com.chai.aiskin.features.auth.LoginScreen
import com.chai.aiskin.features.auth.RegisterScreen
import com.chai.aiskin.features.camera.CameraScreen
import com.chai.aiskin.features.history.CompareScreen
import com.chai.aiskin.features.history.HistoryScreen
import com.chai.aiskin.features.home.HomeScreen
import com.chai.aiskin.features.onboarding.OnboardingScreen
import com.chai.aiskin.features.onboarding.SplashScreen
import com.chai.aiskin.features.preview.PreviewScreen
import com.chai.aiskin.features.profile.ProfileScreen
import com.chai.aiskin.features.result.ResultScreen
import com.chai.aiskin.features.products.ProductsScreen
import com.chai.aiskin.features.scanning.ScanningScreen
import com.chai.aiskin.features.settings.PrivacyScreen
import com.chai.aiskin.features.settings.SettingsScreen
import kotlin.reflect.KClass

private data class Tab(val route: Route, val klass: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, Route.Home::class, "Beranda", Icons.Outlined.Home),
    Tab(Route.Camera, Route.Camera::class, "Pindai", Icons.Outlined.CameraFront),
    Tab(Route.History, Route.History::class, "Riwayat", Icons.Outlined.History),
    Tab(Route.Profile, Route.Profile::class, "Profil", Icons.Outlined.Person),
)

/** Destinations reachable without being signed in. */
private val publicRoutes: List<KClass<*>> = listOf(
    Route.Splash::class, Route.Onboarding::class, Route.Login::class, Route.Register::class, Route.Privacy::class,
)

private fun NavDestination?.isAny(classes: List<KClass<*>>) =
    this?.hierarchy?.any { d -> classes.any { d.hasRoute(it) } } == true

@Composable
fun AppNavHost(nav: NavHostController = rememberNavController(), startVm: StartViewModel = hiltViewModel()) {
    val authState by startVm.authState.collectAsStateWithLifecycle()
    val consent by startVm.consentAccepted.collectAsStateWithLifecycle()
    val start by startVm.startDestination.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    // Auth guard: leaving a protected screen as soon as the session ends (logout, account deletion, revoked token).
    LaunchedEffect(authState, current) {
        if (authState is AuthState.SignedOut && current != null && !current.isAny(publicRoutes)) {
            nav.navigate(Route.Login) { popUpTo(0) { inclusive = true } }
        }
    }

    fun goHome() = nav.navigate(Route.Home) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
    fun openCamera() = if (consent) nav.navigate(Route.Camera) { launchSingleTop = true } else nav.navigate(Route.Privacy)
    fun toLogin() = nav.navigate(Route.Login) { popUpTo(0) { inclusive = true } }

    val showBar = current.isAny(listOf(Route.Home::class, Route.History::class, Route.Profile::class))

    Scaffold(
        bottomBar = { if (showBar) BottomBar(current, onSelect = { tab ->
            if (tab.route == Route.Camera) openCamera()
            else nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        val pad = if (showBar) PaddingValues(bottom = inner.calculateBottomPadding()) else PaddingValues()
        NavHost(nav, startDestination = Route.Splash, modifier = Modifier.padding(pad)) {
            composable<Route.Splash> {
                SplashScreen(start) { dest -> nav.navigate(dest) { popUpTo(Route.Splash) { inclusive = true } } }
            }
            composable<Route.Onboarding> {
                OnboardingScreen(
                    onFinished = { nav.navigate(if (authState is AuthState.SignedIn) Route.Camera else Route.Login) { popUpTo(Route.Onboarding) { inclusive = true } } },
                    onOpenPrivacy = { nav.navigate(Route.Privacy) },
                )
            }
            composable<Route.Login> {
                LoginScreen(
                    onSignedIn = { nav.navigate(Route.Camera) { popUpTo(0) { inclusive = true } } },
                    onRegister = { nav.navigate(Route.Register) },
                )
            }
            composable<Route.Register> {
                RegisterScreen(
                    onRegistered = { nav.navigate(Route.Camera) { popUpTo(0) { inclusive = true } } },
                    onBackToLogin = { nav.popBackStack() },
                )
            }
            composable<Route.Home> {
                HomeScreen(
                    onScan = ::openCamera,
                    onOpenAnalysis = { nav.navigate(Route.Result(it, fresh = false)) },
                    onHistory = { nav.navigate(Route.History) { launchSingleTop = true } },
                    onSettings = { nav.navigate(Route.Settings) },
                )
            }
            composable<Route.Camera> {
                CameraScreen(
                    onClose = { if (!nav.popBackStack()) goHome() },
                    onCaptured = { nav.navigate(Route.Preview) },
                )
            }
            composable<Route.Preview> {
                PreviewScreen(
                    onConfirm = { nav.navigate(Route.Scanning) { popUpTo(Route.Camera) { inclusive = false } } },
                    onRetake = { nav.popBackStack(Route.Camera, inclusive = false) || run { openCamera(); true } },
                )
            }
            composable<Route.Scanning> {
                ScanningScreen(
                    onDone = { id -> nav.navigate(Route.Result(id, fresh = true)) { popUpTo(0) { inclusive = true } } },
                    onRetake = { nav.navigate(Route.Camera) { popUpTo(Route.Camera) { inclusive = true } } },
                    onCancel = { nav.popBackStack(Route.Camera, inclusive = false) },
                    onSignInAgain = ::toLogin,
                )
            }
            composable<Route.Result> { entry ->
                val r = entry.toRoute<Route.Result>()
                ResultScreen(
                    onBack = { nav.popBackStack() },
                    onDone = ::goHome,
                    onScanAgain = { goHome(); openCamera() },
                    onDeleted = { if (r.fresh) goHome() else nav.popBackStack() },
                    onFindProducts = { nav.navigate(Route.Products(r.analysisId)) },
                )
            }
            composable<Route.Products> { ProductsScreen(onBack = { nav.popBackStack() }) }
            composable<Route.History> {
                HistoryScreen(
                    onOpen = { nav.navigate(Route.Result(it, fresh = false)) },
                    onCompare = { a, b -> nav.navigate(Route.Compare(a, b)) },
                    onNewScan = ::openCamera,
                    onSignInAgain = ::toLogin,
                )
            }
            composable<Route.Compare> { CompareScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Profile> {
                ProfileScreen(onSettings = { nav.navigate(Route.Settings) }, onPrivacy = { nav.navigate(Route.Privacy) })
            }
            composable<Route.Settings> {
                SettingsScreen(onBack = { nav.popBackStack() }, onPrivacy = { nav.navigate(Route.Privacy) })
            }
            composable<Route.Privacy> { PrivacyScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun BottomBar(current: NavDestination?, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        tabs.forEach { tab ->
            val selected = current.isAny(listOf(tab.klass))
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = AiSkinTheme.extended.accentSoft,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("tab_${tab.label}"),
            )
        }
    }
}
