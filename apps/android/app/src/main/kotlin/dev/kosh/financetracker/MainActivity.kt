package dev.kosh.financetracker

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.view.WindowManager
import dagger.hilt.android.AndroidEntryPoint
import dev.kosh.financetracker.feature.lock.LockViewModel
import dev.kosh.financetracker.feature.lock.SetupLockScreen
import dev.kosh.financetracker.feature.lock.UnlockScreen
import dev.kosh.financetracker.security.LockState
import dev.kosh.financetracker.security.Vault
import dev.kosh.financetracker.data.settings.Appearance
import dev.kosh.financetracker.feature.accounts.AccountDetailScreen
import dev.kosh.financetracker.feature.accounts.AccountFormScreen
import dev.kosh.financetracker.feature.accounts.AccountsScreen
import dev.kosh.financetracker.feature.addtransaction.AddTransactionScreen
import dev.kosh.financetracker.feature.detail.TransactionDetailScreen
import dev.kosh.financetracker.feature.insights.InsightsScreen
import dev.kosh.financetracker.feature.onboarding.OnboardingScreen
import dev.kosh.financetracker.feature.overview.OverviewScreen
import dev.kosh.financetracker.feature.plan.PlanScreen
import dev.kosh.financetracker.feature.review.ReviewScreen
import dev.kosh.financetracker.feature.settings.SettingsScreen
import dev.kosh.financetracker.feature.trail.TrailScreen
import dev.kosh.financetracker.navigation.AppShellViewModel
import dev.kosh.financetracker.navigation.FULL_SCREEN_ROUTES
import dev.kosh.financetracker.navigation.KoshDestination
import dev.kosh.financetracker.ui.components.KoshBottomBar
import dev.kosh.financetracker.ui.theme.KoshTheme

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val shell: AppShellViewModel by viewModels()
    private val lockVm: LockViewModel by viewModels()
    @javax.inject.Inject lateinit var vault: Vault
    @javax.inject.Inject lateinit var debugOptions: dev.kosh.financetracker.security.DebugOptions
    @javax.inject.Inject lateinit var billing: dev.kosh.financetracker.feature.plus.BillingManager

    override fun onStart() {
        super.onStart()
        vault.onForegrounded()
        // Asks Google Play what is active. Touches no financial data, so it is fine while locked.
        billing.refresh()
    }

    override fun onStop() {
        super.onStop()
        vault.onBackgrounded()
        // Backing out of the app (not a rotation) ends the session.
        if (isFinishing) vault.lock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by shell.appearance.collectAsState()
            val onboarded by shell.onboarded.collectAsState()
            val baseCurrency by shell.baseCurrency.collectAsState()
            androidx.compose.runtime.SideEffect { dev.kosh.financetracker.ui.format.BaseCurrency.code = baseCurrency }
            val lockState by vault.state.collectAsState()
            val secureScreen by vault.secureScreen.collectAsState()
            // Hides the app in the recents switcher and blocks screenshots/screen recording.
            // Always on while locked or setting up; user-controllable once unlocked.
            DisposableEffect(secureScreen, lockState) {
                if (secureScreen || lockState != LockState.UNLOCKED) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                onDispose { }
            }
            val dark = when (appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            // Bar icon contrast follows the app theme, not just the system one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            val extraFontScale by debugOptions.fontScale.collectAsState()
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * extraFontScale),
            ) {
            KoshTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The finance UI (and everything that opens the database) exists only while unlocked.
                    when {
                        onboarded == null -> Unit
                        lockState == LockState.NO_PIN && onboarded == false -> OnboardingScreen(onFinish = shell::finishOnboarding)
                        lockState == LockState.NO_PIN -> SetupLockScreen(lockVm)
                        lockState == LockState.LOCKED -> UnlockScreen(lockVm)
                        onboarded == false -> OnboardingScreen(onFinish = shell::finishOnboarding)
                        else -> KoshApp()
                    }
                }
            }
            }
        }
    }
}

private fun NavHostController.navigateTab(destination: KoshDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun KoshApp() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val undoVm: dev.kosh.financetracker.feature.undo.UndoViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        dev.kosh.financetracker.feature.undo.UndoBus.events.collect { e ->
            val r = snackbar.showSnackbar(e.message, actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) undoVm.restore(e.ids)
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen clears the status bar itself (ScreenHeader / Home); the bottom
        // bar reserves its own space via the padding this Scaffold hands back.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute != null && currentRoute !in FULL_SCREEN_ROUTES) {
                KoshBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navController::navigateTab,
                    onAdd = { navController.navigate(KoshDestination.Add.route) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = KoshDestination.Overview.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(KoshDestination.Overview.route) {
                OverviewScreen(
                    onOpenTransaction = { id -> navController.navigate(KoshDestination.Detail.createRoute(id)) },
                    onOpenReview = { navController.navigate(KoshDestination.Review.route) },
                    onSeeAllActivity = { navController.navigateTab(KoshDestination.Trail) },
                    onOpenInsights = { navController.navigate(KoshDestination.Insights.route) },
                    onOpenSettings = { navController.navigate(KoshDestination.Settings.route) },
                    onOpenPlan = { navController.navigateTab(KoshDestination.Plan) },
                    onOpenAccount = { id -> navController.navigate(KoshDestination.AccountDetail.createRoute(id)) },
                    onOpenAccounts = { navController.navigateTab(KoshDestination.Accounts) },
                )
            }
            composable(KoshDestination.Trail.route) {
                TrailScreen(onOpenTransaction = { id -> navController.navigate(KoshDestination.Detail.createRoute(id)) })
            }
            composable(KoshDestination.Plan.route) { PlanScreen(onOpenTimeline = { navController.navigate(KoshDestination.Timeline.route) }, onOpenPlus = { navController.navigate(KoshDestination.Plus.route) }) }
            composable(KoshDestination.Accounts.route) {
                AccountsScreen(
                    onOpenAccount = { id -> navController.navigate(KoshDestination.AccountDetail.createRoute(id)) },
                    onAddAccount = { navController.navigate(KoshDestination.AccountForm.createRoute()) },
                    onOpenDebt = { navController.navigate(KoshDestination.Debt.route) },
                )
            }
            composable(
                KoshDestination.AccountDetail.route,
                arguments = listOf(androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.LongType }),
            ) {
                AccountDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(KoshDestination.AccountForm.createRoute(id)) },
                    onOpenTransaction = { id -> navController.navigate(KoshDestination.Detail.createRoute(id)) },
                )
            }
            composable(
                KoshDestination.AccountForm.route,
                arguments = listOf(androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.LongType }),
            ) { AccountFormScreen(onDone = { navController.popBackStack() }) }
            composable(KoshDestination.Insights.route) { InsightsScreen(onOpenPlus = { navController.navigate(KoshDestination.Plus.route) }, onBack = { navController.popBackStack() }) }
            composable(KoshDestination.Review.route) { ReviewScreen(onBack = { navController.popBackStack() }) }
            composable(KoshDestination.Add.route) { AddTransactionScreen(onDone = { navController.popBackStack() }) }
            composable(
                KoshDestination.Edit.route,
                arguments = listOf(androidx.navigation.navArgument("id") { type = androidx.navigation.NavType.LongType }),
            ) { AddTransactionScreen(onDone = { navController.popBackStack() }) }
            composable(KoshDestination.Debt.route) { dev.kosh.financetracker.feature.debt.DebtPlannerScreen(onBack = { navController.popBackStack() }, onOpenPlus = { navController.navigate(KoshDestination.Plus.route) }, onEditAccount = { id -> navController.navigate(KoshDestination.AccountForm.createRoute(id)) }) }
            composable(KoshDestination.Timeline.route) { dev.kosh.financetracker.feature.timeline.TimelineScreen(onBack = { navController.popBackStack() }, onOpenPlus = { navController.navigate(KoshDestination.Plus.route) }) }
            composable(KoshDestination.Plus.route) { dev.kosh.financetracker.feature.plus.PaywallScreen(onBack = { navController.popBackStack() }) }
            composable(KoshDestination.ImportCsv.route) { dev.kosh.financetracker.feature.importcsv.ImportCsvScreen(onBack = { navController.popBackStack() }) }
            composable(KoshDestination.Deleted.route) { dev.kosh.financetracker.feature.deleted.DeletedScreen(onBack = { navController.popBackStack() }) }
            composable(KoshDestination.Settings.route) { SettingsScreen(onBack = { navController.popBackStack() }, onOpenDeleted = { navController.navigate(KoshDestination.Deleted.route) }, onOpenImportCsv = { navController.navigate(KoshDestination.ImportCsv.route) }, onOpenPlus = { navController.navigate(KoshDestination.Plus.route) }, onOpenRules = { navController.navigate(KoshDestination.Rules.route) }) }
            composable(KoshDestination.Rules.route) { dev.kosh.financetracker.feature.rules.RulesScreen(onBack = { navController.popBackStack() }, onOpenPlus = { navController.navigate(KoshDestination.Plus.route) }) }
            composable(KoshDestination.Detail.route) {
                TransactionDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(KoshDestination.Edit.createRoute(id)) },
                )
            }
        }
    }
}
