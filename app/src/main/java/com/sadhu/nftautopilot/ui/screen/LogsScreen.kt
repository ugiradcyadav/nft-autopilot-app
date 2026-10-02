package com.sadhu.nftautopilot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import com.sadhu.nftautopilot.data.database.entity.AuditLogEntity
import com.sadhu.nftautopilot.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(db: AppDatabase) : ViewModel() {
    val logs: StateFlow<List<AuditLogEntity>> = db.auditLogDao().observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit, viewModel: LogsViewModel = hiltViewModel()) {
    val logs by viewModel.logs.collectAsState()
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AUDIT LOGS", fontFamily = FontFamily.Monospace, color = CyberBlue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            items(logs) { log ->
                val stateColor = if (log.errorMessage != null) ErrorRed else CyberGreen
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row {
                        Text(fmt.format(Date(log.timestamp)), fontSize = 10.sp, color = TextMuted,
                            fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(8.dp))
                        Text(log.entityType, fontSize = 10.sp, color = CyberBlue,
                            fontFamily = FontFamily.Monospace)
                    }
                    Text("${log.operation} ${log.previousState ?: ""} → ${log.newState ?: ""}",
                        fontSize = 12.sp, color = stateColor, fontFamily = FontFamily.Monospace)
                    log.txHash?.let {
                        Text("TX: ${it.take(12)}…", fontSize = 10.sp, color = TextMuted)
                    }
                    log.errorMessage?.let {
                        Text("ERR: $it", fontSize = 10.sp, color = ErrorRed)
                    }
                    Divider(color = BorderDark, thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
