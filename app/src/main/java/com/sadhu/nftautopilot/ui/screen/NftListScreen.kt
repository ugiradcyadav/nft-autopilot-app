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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadhu.nftautopilot.data.database.AppDatabase
import com.sadhu.nftautopilot.data.database.entity.NftEntity
import com.sadhu.nftautopilot.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class NftListViewModel @Inject constructor(private val db: AppDatabase) : ViewModel() {
    private val _collectionId = MutableStateFlow("")
    
    val nfts: StateFlow<List<NftEntity>> = _collectionId.flatMapLatest { cid ->
        if (cid.isBlank()) flowOf(emptyList())
        else db.nftDao().observeByCollection(cid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun setCollection(id: String) { _collectionId.value = id }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NftListScreen(collectionId: String, onBack: () -> Unit, viewModel: NftListViewModel = hiltViewModel()) {
    LaunchedEffect(collectionId) { viewModel.setCollection(collectionId) }
    val nfts by viewModel.nfts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFTs", fontFamily = FontFamily.Monospace, color = CyberBlue) },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("${nfts.size} NFTs", fontSize = 12.sp, color = TextMuted,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
            }
            items(nfts) { nft -> NftListItem(nft) }
        }
    }
}

@Composable
fun NftListItem(nft: NftEntity) {
    val stateColor = when (nft.state) {
        "MINTED"    -> CyberGreen
        "LISTED"    -> CyberBlue
        "SOLD"      -> WarningYellow
        "MINT_FAILED", "MINT_UNKNOWN" -> ErrorRed
        else        -> TextMuted
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = stateColor.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(nft.tokenId?.let { "#$it" } ?: "…", fontSize = 11.sp,
                        color = stateColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(nft.id.take(8) + "…", fontSize = 12.sp, color = TextPrimary,
                    fontFamily = FontFamily.Monospace)
                Text(nft.state, fontSize = 10.sp, color = stateColor, fontFamily = FontFamily.Monospace)
                nft.mintTxHash?.let {
                    Text("TX: ${it.take(10)}…", fontSize = 10.sp, color = TextMuted)
                }
            }
            nft.mintFeeEth?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Fee", fontSize = 10.sp, color = TextMuted)
                    Text("$it MATIC", fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
