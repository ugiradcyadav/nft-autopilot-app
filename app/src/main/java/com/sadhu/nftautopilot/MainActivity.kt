package com.sadhu.nftautopilot
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack


import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sadhu.nftautopilot.service.AutomationForegroundService
import com.sadhu.nftautopilot.ui.screen.*
import com.sadhu.nftautopilot.ui.theme.NFTAutopilotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NFTAutopilotTheme {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) {
                    NFTAutopilotNavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startForegroundService(
            Intent(this, AutomationForegroundService::class.java)
                .setAction(AutomationForegroundService.ACTION_START)
        )
    }
}

@Composable
fun NFTAutopilotNavGraph() {
    val nav: NavHostController = rememberNavController()

    NavHost(navController = nav, startDestination = "dashboard") {

        composable("dashboard") {
            DashboardScreen(onNavigate = { route -> nav.navigate(route) })
        }

        composable("wallet") {
            WalletScreen(onBack = { nav.popBackStack() })
        }

        composable("collections") {
            CollectionsScreen(
                onBack = { nav.popBackStack() },
                onCollectionSelected = { id -> nav.navigate("nfts/$id") }
            )
        }

        composable(
            "nfts/{collectionId}",
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
        ) { backStackEntry ->
            NftListScreen(
                collectionId = backStackEntry.arguments?.getString("collectionId") ?: "",
                onBack = { nav.popBackStack() }
            )
        }

        composable("nfts") {
            NftListScreen(collectionId = "", onBack = { nav.popBackStack() })
        }

        composable("logs") {
            LogsScreen(onBack = { nav.popBackStack() })
        }

        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }

        composable("listings") {
            PlaceholderScreen("LISTINGS — Phase 9+") { nav.popBackStack() }
        }

        composable("sales") {
            PlaceholderScreen("SALES — Phase 11+") { nav.popBackStack() }
        }

        composable("security") {
            PlaceholderScreen("SECURITY CENTER — Phase 15+") { nav.popBackStack() }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    androidx.compose.material3.Text(title,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = com.sadhu.nftautopilot.ui.theme.CyberBlue,
                        fontSize = androidx.compose.ui.unit.sp(13))
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = com.sadhu.nftautopilot.ui.theme.TextPrimary
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = com.sadhu.nftautopilot.ui.theme.SurfaceDark)
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text(
                "$title\nImplemented in subsequent phase.",
                color = com.sadhu.nftautopilot.ui.theme.TextMuted,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = androidx.compose.ui.unit.sp(13),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
