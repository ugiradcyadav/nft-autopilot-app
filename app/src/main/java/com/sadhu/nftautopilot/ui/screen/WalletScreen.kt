package com.sadhu.nftautopilot.ui.screen

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.sadhu.nftautopilot.data.database.AppDatabase
import com.sadhu.nftautopilot.data.database.entity.WalletEntity
import com.sadhu.nftautopilot.engine.wallet.WalletSession
import com.sadhu.nftautopilot.engine.wallet.WalletVault
import com.sadhu.nftautopilot.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val db: AppDatabase,
    private val walletVault: WalletVault,
    val walletSession: WalletSession
) : ViewModel() {
    val wallets: StateFlow<List<WalletEntity>> = db.walletDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createWallet(label: String, type: String, chainId: Long) {
        viewModelScope.launch {
            walletVault.createWallet(label, type, chainId)
        }
    }

    fun startSession(walletId: String) = walletSession.startSession(walletId)
    fun invalidateSession() = walletSession.invalidate()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel()
) {
    val wallets by viewModel.wallets.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var sessionStatus by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WALLETS", fontFamily = FontFamily.Monospace, color = CyberBlue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create wallet", tint = CyberGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Session status
            item {
                SessionStatusCard(
                    isValid = viewModel.walletSession.isSessionValid(),
                    remainingMs = viewModel.walletSession.remainingMs(),
                    statusMessage = sessionStatus,
                    onAuthenticate = {
                        authenticateWithBiometric(
                            activity = context as FragmentActivity,
                            onSuccess = { walletId ->
                                viewModel.startSession(walletId)
                                sessionStatus = "Session active (30 min)"
                            },
                            onError = { sessionStatus = "Auth failed: $it" }
                        )
                    },
                    onLock = {
                        viewModel.invalidateSession()
                        sessionStatus = "Session locked"
                    }
                )
            }
            items(wallets) { wallet ->
                WalletCard(wallet = wallet)
            }
            if (wallets.isEmpty()) {
                item {
                    Text(
                        "No wallets configured.\nTap + to create your first wallet.",
                        color = TextMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(32.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateWalletDialog(
            onConfirm = { label, type ->
                viewModel.createWallet(label, type, 80002L)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun SessionStatusCard(
    isValid: Boolean,
    remainingMs: Long,
    statusMessage: String,
    onAuthenticate: () -> Unit,
    onLock: () -> Unit
) {
    val remainingMin = remainingMs / 60_000
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, if (isValid) CyberGreen else WarningYellow)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isValid) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isValid) CyberGreen else WarningYellow
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (isValid) "SESSION ACTIVE — ${remainingMin}m remaining" else "SESSION LOCKED",
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isValid) CyberGreen else WarningYellow
                    )
                    if (statusMessage.isNotBlank()) {
                        Text(statusMessage, fontSize = 11.sp, color = TextMuted)
                    }
                    Text("Biometric auth unlocks signing for 30 min",
                        fontSize = 10.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isValid) {
                    Button(
                        onClick = onAuthenticate,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        modifier = Modifier.weight(1f)
                    ) { Text("AUTHENTICATE", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                } else {
                    OutlinedButton(
                        onClick = onLock,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, WarningYellow)
                    ) { Text("LOCK SESSION", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = WarningYellow) }
                }
            }
        }
    }
}

@Composable
fun WalletCard(wallet: WalletEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (wallet.walletType == "HOT") Icons.Default.Bolt else Icons.Default.Security,
                    contentDescription = null, tint = CyberBlue, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(wallet.label, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Text(wallet.walletType, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                }
                Surface(shape = MaterialTheme.shapes.small,
                    color = if (wallet.isActive) CyberGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)) {
                    Text(
                        if (wallet.isActive) "ACTIVE" else "INACTIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = if (wallet.isActive) CyberGreen else ErrorRed
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Address — never show private key
            Text("ADDRESS", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Text(wallet.address, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text("Chain ID: ${wallet.chainId}", fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
fun CreateWalletDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("HOT") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Wallet", fontFamily = FontFamily.Monospace) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("Wallet label") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("HOT", "TREASURY").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }
                Text("Private key is generated on-device and encrypted with Android Keystore.",
                    fontSize = 11.sp, color = TextMuted)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (label.isNotBlank()) onConfirm(label, type) }) {
                Text("CREATE", color = CyberGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

fun authenticateWithBiometric(
    activity: FragmentActivity,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess("session")
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError(errString.toString())
        }
        override fun onAuthenticationFailed() {
            onError("Authentication failed")
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Authenticate to sign transactions")
        .setSubtitle("Biometric or device PIN required")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}
