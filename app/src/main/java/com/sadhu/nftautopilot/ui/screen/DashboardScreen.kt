package com.sadhu.nftautopilot.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.sadhu.nftautopilot.engine.automation.AutomationState
import com.sadhu.nftautopilot.ui.theme.*
import com.sadhu.nftautopilot.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val autoStatus by viewModel.automationStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("NFT AUTOPILOT", fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CyberGreen)
                },
                actions = {
                    val color = if (uiState.networkStatus.connected) CyberGreen else ErrorRed
                    Icon(Icons.Default.Circle, contentDescription = "Network", tint = color,
                        modifier = Modifier.padding(end = 16.dp).size(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Status bar
            item {
                AutomationStatusCard(
                    state = autoStatus.state,
                    operation = autoStatus.currentOperation,
                    pendingJobs = uiState.pendingJobs,
                    onStart = viewModel::startAutomation,
                    onPause = viewModel::pauseAutomation,
                    onStop = viewModel::stopAutomation
                )
            }
            // ── Stats grid
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Created", uiState.totalCreated.toString(), Modifier.weight(1f))
                    StatCard("Minted",  uiState.totalMinted.toString(),  Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Listed", uiState.totalListed.toString(), Modifier.weight(1f))
                    StatCard("Sold",   uiState.totalSold.toString(),   Modifier.weight(1f))
                }
            }
            // ── Revenue
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("NET REVENUE", fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp, color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (uiState.netRevenueEth == "0.00") "N/A" else "${uiState.netRevenueEth} MATIC",
                            fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            color = CyberGreen, fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Verified on-chain sales only", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
            // ── Wallet
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = CyberBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("HOT WALLET", fontSize = 10.sp, color = TextMuted,
                                fontFamily = FontFamily.Monospace)
                            Text(
                                if (uiState.walletAddress.length > 12)
                                    "${uiState.walletAddress.take(8)}…${uiState.walletAddress.takeLast(6)}"
                                else "Not configured",
                                fontSize = 13.sp, color = TextPrimary, fontFamily = FontFamily.Monospace
                            )
                        }
                        Text("${uiState.walletBalanceEth} MATIC", fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, color = CyberGreen)
                    }
                }
            }
            // ── Health indicators
            item {
                HealthIndicatorRow(
                    blockchain = uiState.networkStatus.connected,
                    marketplace = true, // TODO: real check
                    storage = true,
                    database = true
                )
            }
            // ── Navigation buttons
            item { NavigationGrid(onNavigate) }
        }
    }
}

@Composable
fun AutomationStatusCard(
    state: AutomationState,
    operation: String?,
    pendingJobs: Int,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit
) {
    val stateColor = when (state) {
        AutomationState.RUNNING -> CyberGreen
        AutomationState.PAUSED  -> WarningYellow
        AutomationState.ERROR   -> ErrorRed
        AutomationState.IDLE    -> TextMuted
    }
    val stateLabel = state.name

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, stateColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Circle, contentDescription = null, tint = stateColor, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(8.dp))
                Text("AUTOMATION: $stateLabel", fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = stateColor)
                Spacer(Modifier.weight(1f))
                Text("$pendingJobs pending", fontSize = 11.sp, color = TextMuted)
            }
            if (operation != null) {
                Spacer(Modifier.height(6.dp))
                Text(operation, fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state != AutomationState.RUNNING) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        modifier = Modifier.weight(1f)
                    ) { Text("START", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Black) }
                } else {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, WarningYellow)) {
                        Text("PAUSE", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = WarningYellow)
                    }
                }
                // Emergency Stop
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.weight(1f)
                ) { Text("STOP ALL", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
fun HealthIndicatorRow(blockchain: Boolean, marketplace: Boolean, storage: Boolean, database: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()) {
            HealthDot("CHAIN",  blockchain)
            HealthDot("MARKET", marketplace)
            HealthDot("IPFS",   storage)
            HealthDot("DB",     database)
        }
    }
}

@Composable
fun HealthDot(label: String, ok: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Circle, contentDescription = null,
            tint = if (ok) CyberGreen else ErrorRed, modifier = Modifier.size(10.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun NavigationGrid(onNavigate: (String) -> Unit) {
    val navItems = listOf(
        Triple("Collections", Icons.Default.Collections, "collections"),
        Triple("NFTs", Icons.Default.Image, "nfts"),
        Triple("Wallet", Icons.Default.AccountBalanceWallet, "wallet"),
        Triple("Listings", Icons.Default.Sell, "listings"),
        Triple("Sales", Icons.Default.MonetizationOn, "sales"),
        Triple("Logs", Icons.Default.Article, "logs"),
        Triple("Security", Icons.Default.Security, "security"),
        Triple("Settings", Icons.Default.Settings, "settings")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        navItems.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, icon, route) ->
                    OutlinedButton(
                        onClick = { onNavigate(route) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Icon(icon, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
