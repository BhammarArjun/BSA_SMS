package com.local.smsllm.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.local.smsllm.ui.dashboard.DashboardScreen
import com.local.smsllm.ui.detail.DetailScreen
import com.local.smsllm.ui.onboarding.OnboardingScreen
import com.local.smsllm.ui.settings.SettingsScreen
import com.local.smsllm.ui.theme.BrandGold
import com.local.smsllm.ui.transactions.TransactionsScreen

// ── Route constants ───────────────────────────────────────────────────────────
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{id}"
    fun detail(id: Long) = "detail/$id"
}

// ── Bottom nav items ──────────────────────────────────────────────────────────
private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

private val NAV_ITEMS = listOf(
    NavItem("Ledger", Icons.Default.AccountBalanceWallet, Routes.DASHBOARD),
    NavItem("History", Icons.Default.List, Routes.TRANSACTIONS),
    NavItem("Settings", Icons.Default.Settings, Routes.SETTINGS),
)

private val BOTTOM_NAV_ROUTES = setOf(Routes.DASHBOARD, Routes.TRANSACTIONS, Routes.SETTINGS)

// ── Root NavHost ──────────────────────────────────────────────────────────────
@Composable
fun AppNav(
    gateViewModel: AppGateViewModel = hiltViewModel(),
) {
    val gateState by gateViewModel.state.collectAsState()
    val navController = rememberNavController()

    // Once gate is checked, navigate to the correct start
    LaunchedEffect(gateState.checked) {
        if (!gateState.checked) return@LaunchedEffect
        val ready = gateState.smsPermissionsGranted && gateState.modelReady
        if (!ready) {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        } else {
            navController.navigate(Routes.DASHBOARD) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in BOTTOM_NAV_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                LedgerBottomNav(
                    currentDestination = navBackStackEntry?.destination,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ONBOARDING,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        gateViewModel.recheck()
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen()
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onTransactionClick = { id ->
                        navController.navigate(Routes.detail(id))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onPermissionsChanged = { gateViewModel.recheck() },
                )
            }
            composable(Routes.DETAIL) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                DetailScreen(
                    transactionId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

// ── Bottom navigation bar ─────────────────────────────────────────────────────
@Composable
private fun LedgerBottomNav(
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = androidx.compose.ui.unit.Dp(0f),
    ) {
        NAV_ITEMS.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandGold,
                    selectedTextColor = BrandGold,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
