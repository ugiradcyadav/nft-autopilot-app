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
import com.sadhu.nftautopilot.data.database.entity.CollectionEntity
import com.sadhu.nftautopilot.engine.automation.AutomationEngine
import com.sadhu.nftautopilot.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val db: AppDatabase,
    private val automationEngine: AutomationEngine
) : ViewModel() {
    val collections: StateFlow<List<CollectionEntity>> = db.collectionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCollection(
        name: String, symbol: String, description: String,
        supply: Int, chainId: Long, ownerAddress: String, royaltyBps: Int
    ) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            db.collectionDao().insert(CollectionEntity(
                id = id,
                name = name,
                symbol = symbol,
                description = description,
                ownerAddress = ownerAddress,
                minterAddress = ownerAddress,
                chainId = chainId,
                totalSupply = supply,
                royaltyBps = royaltyBps,
                royaltyAddress = ownerAddress
            ))
        }
    }

    fun startGeneration(collectionId: String) {
        viewModelScope.launch {
            automationEngine.enqueueCollectionGeneration(collectionId)
            automationEngine.start()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onCollectionSelected: (String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val collections by viewModel.collections.collectAsState()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("COLLECTIONS", fontFamily = FontFamily.Monospace, color = CyberBlue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New", tint = CyberGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(collections) { col ->
                CollectionCard(
                    collection = col,
                    onClick = { onCollectionSelected(col.id) },
                    onGenerate = { viewModel.startGeneration(col.id) }
                )
            }
            if (collections.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Collections, contentDescription = null,
                                tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No collections yet.\nTap + to create your first.",
                                color = TextMuted, fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateCollectionDialog(
            onConfirm = { name, symbol, desc, supply ->
                viewModel.createCollection(name, symbol, desc, supply, 80002L, "", 500)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }
}

@Composable
fun CollectionCard(
    collection: CollectionEntity,
    onClick: () -> Unit,
    onGenerate: () -> Unit
) {
    val statusColor = when (collection.status) {
        "DEPLOYED" -> CyberGreen
        "MINTING"  -> CyberBlue
        "COMPLETE" -> WarningYellow
        else       -> TextMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(collection.name, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary, fontSize = 16.sp)
                    Text("${collection.symbol} · Chain ${collection.chainId}",
                        fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(collection.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = statusColor)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            val progress = if (collection.totalSupply > 0)
                collection.mintedCount.toFloat() / collection.totalSupply else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = CyberGreen,
                trackColor = BorderDark
            )
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                StatPill("Minted", "${collection.mintedCount}/${collection.totalSupply}")
                StatPill("Listed", collection.listedCount.toString())
                StatPill("Sold", collection.soldCount.toString())
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("GENERATE", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = androidx.compose.ui.graphics.Color.Black)
                }
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, CyberBlue)
                ) {
                    Text("VIEW NFTs", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyberBlue)
                }
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun CreateCollectionDialog(
    onConfirm: (String, String, String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var supply by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Collection", fontFamily = FontFamily.Monospace, color = CyberGreen) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Collection Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = symbol, onValueChange = { symbol = it.uppercase() },
                    label = { Text("Symbol (e.g. CB47)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Description") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = supply,
                    onValueChange = { if (it.all { c -> c.isDigit() }) supply = it },
                    label = { Text("Total Supply") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = supply.toIntOrNull() ?: 100
                    if (name.isNotBlank() && symbol.isNotBlank()) onConfirm(name, symbol, description, s)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen)
            ) { Text("CREATE", fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}
