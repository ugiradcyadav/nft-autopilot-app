package com.sadhu.nftautopilot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadhu.nftautopilot.data.database.AppDatabase
import com.sadhu.nftautopilot.data.database.entity.SystemSettingEntity
import com.sadhu.nftautopilot.engine.blockchain.PolygonRpcProvider
import com.sadhu.nftautopilot.engine.ipfs.IpfsStorageEngine
import com.sadhu.nftautopilot.engine.marketplace.OpenSeaAdapter
import com.sadhu.nftautopilot.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val db: AppDatabase,
    private val polygonRpc: PolygonRpcProvider,
    private val ipfsEngine: IpfsStorageEngine,
    private val openSeaAdapter: OpenSeaAdapter
) : ViewModel() {

    fun savePinataJwt(jwt: String) {
        viewModelScope.launch {
            db.settingsDao().set(SystemSettingEntity("pinata_jwt", jwt))
            ipfsEngine.pinataJwt = jwt
        }
    }

    fun saveOpenSeaKey(key: String) {
        viewModelScope.launch {
            db.settingsDao().set(SystemSettingEntity("opensea_api_key", key))
            openSeaAdapter.apiKey = key
        }
    }

    fun saveRpcUrl(url: String) {
        viewModelScope.launch {
            db.settingsDao().set(SystemSettingEntity("rpc_url", url))
            polygonRpc.rpcUrl = url
        }
    }

    fun setProductionMode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                polygonRpc.chainId = 137L
                polygonRpc.rpcUrl = "https://polygon-rpc.com"
                polygonRpc.polygonScanBaseUrl = "https://polygonscan.com"
                openSeaAdapter.chain = "matic"
                db.settingsDao().set(SystemSettingEntity("production_mode", "true"))
            } else {
                polygonRpc.chainId = 80002L
                polygonRpc.rpcUrl = "https://rpc-amoy.polygon.technology"
                polygonRpc.polygonScanBaseUrl = "https://amoy.polygonscan.com"
                openSeaAdapter.chain = "amoy"
                db.settingsDao().set(SystemSettingEntity("production_mode", "false"))
            }
        }
    }

    suspend fun loadSettings() {
        db.settingsDao().get("pinata_jwt")?.let { ipfsEngine.pinataJwt = it }
        db.settingsDao().get("opensea_api_key")?.let { openSeaAdapter.apiKey = it }
        db.settingsDao().get("rpc_url")?.let { polygonRpc.rpcUrl = it }
        val prodMode = db.settingsDao().get("production_mode") == "true"
        if (prodMode) setProductionMode(true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val pinataJwt by viewModel.pinataJwt.collectAsState(initial = "")
    val openSeaKey by viewModel.openSeaKey.collectAsState(initial = "")
    val rpcUrl by viewModel.rpcUrl.collectAsState(initial = "")
    var productionMode by remember { mutableStateOf(false) }
    var showProdConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", fontFamily = FontFamily.Monospace, color = CyberBlue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = "STORAGE (IPFS / PINATA)") {
                    OutlinedTextField(
                        value = pinataJwt,
                        onValueChange = { viewModel.updateTempPinataJwt(it) },
                        label = { Text("Pinata JWT", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.savePinataJwt(pinataJwt) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                    ) { Text("SAVE JWT", fontFamily = FontFamily.Monospace) }
                }
            }
            item {
                SettingsSection(title = "MARKETPLACE (OPENSEA)") {
                    OutlinedTextField(
                        value = openSeaKey,
                        onValueChange = { viewModel.updateTempOpenSeaKey(it) },
                        label = { Text("OpenSea API Key", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveOpenSeaKey(openSeaKey) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                    ) { Text("SAVE KEY", fontFamily = FontFamily.Monospace) }
                }
            }
            item {
                SettingsSection(title = "BLOCKCHAIN") {
                    OutlinedTextField(
                        value = rpcUrl, onValueChange = { viewModel.updateTempRpcUrl(it) },
                        label = { Text("RPC URL", fontSize = 12.sp) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.saveRpcUrl(rpcUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)) {
                        Text("SAVE RPC", fontFamily = FontFamily.Monospace)
                    }
                }
            }
            item {
                SettingsSection(title = "NETWORK MODE") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(
                            checked = productionMode,
                            onCheckedChange = { enabled ->
                                if (enabled) showProdConfirm = true else {
                                    productionMode = false
                                    viewModel.setProductionMode(false)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = ErrorRed, checkedTrackColor = ErrorRed.copy(alpha = 0.5f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (productionMode) "MAINNET (Chain ID: 137)" else "TESTNET (Amoy — Chain ID: 80002)",
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                color = if (productionMode) ErrorRed else CyberGreen)
                            Text(if (productionMode) "REAL MATIC — REAL TRANSACTIONS" else "Test funds only",
                                fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }

    if (showProdConfirm) {
        AlertDialog(
            onDismissRequest = { showProdConfirm = false },
            title = { Text("⚠️ Switch to Mainnet", color = ErrorRed) },
            text = {
                Text("This will use REAL Polygon mainnet with REAL MATIC.\nAll transactions cost real money and are irreversible.\n\nAre you absolutely certain?",
                    color = TextPrimary, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    productionMode = true
                    viewModel.setProductionMode(true)
                    showProdConfirm = false
                }) { Text("CONFIRM MAINNET", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showProdConfirm = false }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}
