package com.ejournal.skaduta.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ejournal.skaduta.ui.components.AppUpdateDialog
import com.ejournal.skaduta.ui.theme.BrandOrangeContainer
import com.ejournal.skaduta.ui.theme.BrandOrangeDark
import com.ejournal.skaduta.ui.theme.SlateTextSecondary
import com.ejournal.skaduta.ui.screens.entry.AddEditJournalScreen
import com.ejournal.skaduta.ui.screens.entry.AddEditViewModel
import com.ejournal.skaduta.ui.screens.home.HomeScreen
import com.ejournal.skaduta.ui.screens.home.HomeViewModel
import com.ejournal.skaduta.ui.screens.profile.ProfileScreen
import com.ejournal.skaduta.ui.screens.profile.ProfileViewModel
import com.ejournal.skaduta.ui.screens.report.MonthlyReportScreen
import com.ejournal.skaduta.ui.screens.report.MonthlyReportViewModel
import com.ejournal.skaduta.util.AppUpdateManager

@Composable
fun MainAppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()

    val showUpdateDialog by AppUpdateManager.showUpdateDialog.collectAsState()
    val updateInfo by AppUpdateManager.updateInfo.collectAsState()
    val isDownloading by AppUpdateManager.isDownloading.collectAsState()
    val downloadProgress by AppUpdateManager.downloadProgress.collectAsState()

    // Otomatis periksa pembaruan dari GitHub Releases di latar belakang saat aplikasi dibuka
    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdate(context, isManual = false)
    }

    if (showUpdateDialog && updateInfo != null) {
        AppUpdateDialog(
            updateInfo = updateInfo!!,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onConfirmUpdate = {
                AppUpdateManager.downloadAndInstall(context, updateInfo!!)
            },
            onDismiss = {
                AppUpdateManager.dismissUpdateDialog()
            }
        )
    }

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.MonthlyReport,
        Screen.Profile
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar when in AddEditJournal
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                screen.icon?.let {
                                    Icon(imageVector = it, contentDescription = screen.title)
                                }
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = BrandOrangeContainer,
                                selectedIconColor = BrandOrangeDark,
                                selectedTextColor = BrandOrangeDark,
                                unselectedIconColor = SlateTextSecondary,
                                unselectedTextColor = SlateTextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToAddEdit = { entryId ->
                        navController.navigate(Screen.AddEditJournal.createRoute(entryId))
                    }
                )
            }

            composable(Screen.MonthlyReport.route) {
                val reportViewModel: MonthlyReportViewModel = viewModel()
                MonthlyReportScreen(viewModel = reportViewModel)
            }

            composable(Screen.Profile.route) {
                val profileViewModel: ProfileViewModel = viewModel()
                ProfileScreen(viewModel = profileViewModel)
            }

            composable(
                route = Screen.AddEditJournal.route,
                arguments = listOf(
                    navArgument("entryId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getLong("entryId") ?: -1L
                val addEditViewModel: AddEditViewModel = viewModel()
                AddEditJournalScreen(
                    entryId = entryId,
                    viewModel = addEditViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

