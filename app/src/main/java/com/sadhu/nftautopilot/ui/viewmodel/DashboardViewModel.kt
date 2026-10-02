package com.sadhu.nftautopilot.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadhu.nftautopilot.data.database.AppDatabase
import com.sadhu.nftautopilot.engine.automation.AutomationEngine
import com.sadhu.nftautopilot.engine.automation.AutomationStatus
import com.sadhu.nftautopilot.engine.blockchain.NetworkStatus
import com.sadhu.nftautopilot.engine.blockchain.PolygonRpcProvider
import com.sadhu.nftautopilot.engine.wallet.WalletSession
import com.sadhu.nftautopilot.service.AutomationForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val totalCreated: Int = 0,
    val totalMinted: Int = 0,
    val totalListed: Int = 0,
    val totalSold: Int = 0,
    val netRevenueEth: String = "0.00",
    val pendingJobs: Int = 0,
    val walletAddress: String = "",
    val walletBalanceEth: String = "…",
    val automationStatus: AutomationStatus = AutomationStatus(),
    val networkStatus: NetworkStatus = NetworkStatus(false, -1, null, null),
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val automationEngine: AutomationEngine,
    private val polygonRpc: PolygonRpcProvider,
    private val walletSession: WalletSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val automationStatus: StateFlow<AutomationStatus> = automationEngine.status

    init {
        observeStats()
        observeNetworkStatus()
    }

    private fun observeStats() {
        viewModelScope.launch {
            combine(
                db.automationJobDao().observePendingCount(),
                db.auditLogDao().observeRecent(1)
            ) { pending, _ -> pending }.collect { pending ->
                val minted = db.nftDao().countByState("", "MINTED") // simplified
                val netWei = db.saleDao().getTotalNetRevenueWei() ?: 0.0
                val netEth = org.web3j.utils.Convert.fromWei(
                    netWei.toBigDecimal(), org.web3j.utils.Convert.Unit.ETHER
                ).toPlainString()
                val wallet = db.walletDao().getActiveHotWallet()

                _uiState.update {
                    it.copy(
                        pendingJobs = pending,
                        netRevenueEth = netEth,
                        walletAddress = wallet?.address ?: "",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            while (true) {
                val status = polygonRpc.getNetworkStatus()
                _uiState.update { it.copy(networkStatus = status) }

                val wallet = db.walletDao().getActiveHotWallet()
                if (wallet != null && status.connected) {
                    try {
                        val balance = polygonRpc.getBalanceEth(wallet.address)
                        _uiState.update { it.copy(walletBalanceEth = balance) }
                    } catch (_: Exception) {}
                }
                delay(30_000)
            }
        }
    }

    fun startAutomation() {
        val intent = Intent(context, AutomationForegroundService::class.java)
            .setAction(AutomationForegroundService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pauseAutomation() {
        context.startService(
            Intent(context, AutomationForegroundService::class.java)
                .setAction(AutomationForegroundService.ACTION_PAUSE)
        )
    }

    fun stopAutomation() {
        automationEngine.pause()
        context.startService(
            Intent(context, AutomationForegroundService::class.java)
                .setAction(AutomationForegroundService.ACTION_STOP)
        )
    }

    fun isSessionValid() = walletSession.isSessionValid()
}
