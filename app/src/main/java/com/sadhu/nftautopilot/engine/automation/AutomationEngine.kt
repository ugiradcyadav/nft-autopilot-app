package com.sadhu.nftautopilot.engine.automation

import android.util.Log
import com.google.gson.Gson
import com.sadhu.nftautopilot.data.database.AppDatabase
import com.sadhu.nftautopilot.data.database.entity.*
import com.sadhu.nftautopilot.engine.artwork.ArtworkGenerator
import com.sadhu.nftautopilot.engine.blockchain.PolygonRpcProvider
import com.sadhu.nftautopilot.engine.ipfs.IpfsStorageEngine
import com.sadhu.nftautopilot.engine.marketplace.OpenSeaAdapter
import com.sadhu.nftautopilot.engine.wallet.WalletSession
import com.sadhu.nftautopilot.util.AuditLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class AutomationState { IDLE, RUNNING, PAUSED, ERROR }

data class AutomationStatus(
    val state: AutomationState = AutomationState.IDLE,
    val currentJobId: String? = null,
    val currentOperation: String? = null,
    val pendingJobCount: Int = 0,
    val lastError: String? = null
)

@Singleton
class AutomationEngine @Inject constructor(
    private val db: AppDatabase,
    private val artworkGenerator: ArtworkGenerator,
    private val polygonRpc: PolygonRpcProvider,
    private val ipfsEngine: IpfsStorageEngine,
    private val openSeaAdapter: OpenSeaAdapter,
    private val walletSession: WalletSession,
    private val auditLogger: AuditLogger,
    private val nftStateMachine: NftStateMachine
) {
    private val TAG = "AutomationEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val _status = MutableStateFlow(AutomationStatus())
    val status: StateFlow<AutomationStatus> = _status

    var isRunning = false
        private set

    var spendingLimitWeiPerTx: java.math.BigInteger = java.math.BigInteger.ZERO // 0 = no limit

    // ─── Start / Stop ─────────────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        isRunning = true
        _status.value = AutomationStatus(AutomationState.RUNNING)
        Log.i(TAG, "Automation engine started")
        scope.launch { runJobLoop() }
        scope.launch { runRecoveryLoop() }
    }

    fun pause() {
        isRunning = false
        _status.value = _status.value.copy(state = AutomationState.PAUSED)
        Log.i(TAG, "Automation engine paused")
    }

    fun resume() {
        if (!isRunning) start()
    }

    // ─── Job enqueue ──────────────────────────────────────────────────────────

    suspend fun enqueueCollectionGeneration(collectionId: String): String {
        val jobId = UUID.randomUUID().toString()
        val key = "GEN_$collectionId"
        db.automationJobDao().insert(AutomationJobEntity(
            id = jobId,
            jobType = "GENERATE_COLLECTION",
            collectionId = collectionId,
            idempotencyKey = key,
            payload = gson.toJson(mapOf("collectionId" to collectionId))
        ))
        return jobId
    }

    suspend fun enqueueMint(nftId: String, walletId: String): String {
        val jobId = UUID.randomUUID().toString()
        val key = "MINT_$nftId"
        db.automationJobDao().insert(AutomationJobEntity(
            id = jobId,
            jobType = "MINT",
            nftId = nftId,
            idempotencyKey = key,
            payload = gson.toJson(mapOf("nftId" to nftId, "walletId" to walletId))
        ))
        return jobId
    }

    suspend fun enqueueListing(nftId: String, priceWei: String): String {
        val jobId = UUID.randomUUID().toString()
        val key = "LIST_$nftId"
        db.automationJobDao().insert(AutomationJobEntity(
            id = jobId,
            jobType = "LIST",
            nftId = nftId,
            idempotencyKey = key,
            payload = gson.toJson(mapOf("nftId" to nftId, "priceWei" to priceWei))
        ))
        return jobId
    }

    // ─── Main job loop ────────────────────────────────────────────────────────

    private suspend fun runJobLoop() {
        while (isRunning) {
            try {
                val jobs = db.automationJobDao().getNextPending(limit = 3)
                if (jobs.isEmpty()) {
                    delay(5_000)
                    continue
                }

                for (job in jobs) {
                    if (!isRunning) break
                    processJob(job)
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Job loop error", e)
                delay(10_000)
            }
        }
    }

    private suspend fun processJob(job: AutomationJobEntity) {
        _status.value = _status.value.copy(
            currentJobId = job.id,
            currentOperation = job.jobType
        )
        db.automationJobDao().setRunning(job.id)

        try {
            when (job.jobType) {
                "GENERATE_COLLECTION" -> handleGenerateCollection(job)
                "UPLOAD_IMAGE"        -> handleUploadImage(job)
                "UPLOAD_METADATA"     -> handleUploadMetadata(job)
                "MINT"                -> handleMint(job)
                "VERIFY_MINT"         -> handleVerifyMint(job)
                "LIST"                -> handleList(job)
                "VERIFY_LISTING"      -> handleVerifyListing(job)
                "MONITOR_SALES"       -> handleMonitorSales(job)
                else -> {
                    Log.w(TAG, "Unknown job type: ${job.jobType}")
                    db.automationJobDao().setDone(job.id, "FAILED")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Job ${job.id} (${job.jobType}) failed: ${e.message}", e)
            val newRetry = job.retryCount + 1
            val finalState = if (newRetry >= job.maxRetries) "FAILED" else "RETRY"
            db.automationJobDao().setError(job.id, finalState, e.message ?: "Unknown error")
            auditLogger.log(
                entityType = "JOB", entityId = job.id,
                operation = job.jobType, previousState = "RUNNING",
                newState = finalState, errorMessage = e.message
            )
        } finally {
            _status.value = _status.value.copy(currentJobId = null, currentOperation = null)
        }
    }

    // ─── Job handlers ─────────────────────────────────────────────────────────

    private suspend fun handleGenerateCollection(job: AutomationJobEntity) {
        val collectionId = job.collectionId ?: error("No collectionId in job")
        val collection = db.collectionDao().getById(collectionId) ?: error("Collection not found")
        val existing = db.artworkDao().getByCollection(collectionId).size

        for (index in existing until collection.totalSupply) {
            if (!isRunning) break
            val result = artworkGenerator.generateOne(collectionId, index)
            if (result != null && !result.isDuplicate) {
                // Enqueue metadata upload
                db.automationJobDao().insert(AutomationJobEntity(
                    id = UUID.randomUUID().toString(),
                    jobType = "UPLOAD_IMAGE",
                    nftId = result.artworkId,
                    collectionId = collectionId,
                    idempotencyKey = "UPLOAD_IMG_${result.artworkId}",
                    payload = gson.toJson(mapOf("artworkId" to result.artworkId))
                ))
            }
        }
        db.automationJobDao().setDone(job.id, "SUCCESS")
    }

    private suspend fun handleUploadImage(job: AutomationJobEntity) {
        val payload = gson.fromJson(job.payload, Map::class.java)
        val artworkId = payload["artworkId"]?.toString() ?: error("No artworkId")
        val artwork = db.artworkDao().getByCollection(job.collectionId ?: "")
            .firstOrNull { it.id == artworkId } ?: error("Artwork not found")

        db.metadataDao().setImageStatus(
            db.metadataDao().run { /* find by artworkId */ artworkId }, "UPLOADING"
        )

        val result = ipfsEngine.uploadImage(
            java.io.File(artwork.localFilePath),
            "NFT-${artwork.tokenIndex}"
        )

        // Verify CID is accessible
        val verified = ipfsEngine.verifyCid(result.cid)
        if (!verified) error("IPFS CID verification failed: ${result.cid}")

        // Update metadata record
        val meta = db.metadataDao().getById(artworkId)
        if (meta != null) {
            db.metadataDao().setImageUploaded(meta.id, result.cid, result.uri)
        }

        db.automationJobDao().setDone(job.id, "SUCCESS")
    }

    private suspend fun handleUploadMetadata(job: AutomationJobEntity) {
        val payload = gson.fromJson(job.payload, Map::class.java)
        val metadataId = payload["metadataId"]?.toString() ?: error("No metadataId")
        val meta = db.metadataDao().getById(metadataId) ?: error("Metadata not found")

        require(meta.imageCid != null) { "Image CID not set — upload image first" }

        // Build ERC-721 metadata JSON
        val metadataJson = buildMetadataJson(meta)

        db.metadataDao().setMetaStatus(metadataId, "UPLOADING")
        val result = ipfsEngine.uploadMetadata(metadataJson, meta.tokenName)

        val verified = ipfsEngine.verifyCid(result.cid)
        if (!verified) error("Metadata CID verification failed: ${result.cid}")

        db.metadataDao().setMetaUploaded(metadataId, result.cid, result.uri)
        db.automationJobDao().setDone(job.id, "SUCCESS")
    }

    private suspend fun handleMint(job: AutomationJobEntity) {
        val nftId = job.nftId ?: error("No nftId")
        val payload = gson.fromJson(job.payload, Map::class.java)
        val walletId = payload["walletId"]?.toString() ?: error("No walletId")

        // IDEMPOTENCY: Check if already minted
        val mintJob = db.mintJobDao().getLatestForNft(nftId)
        if (mintJob?.txHash != null) {
            val txStatus = polygonRpc.getTxStatus(mintJob.txHash)
            if (txStatus == "CONFIRMED_SUCCESS") {
                Log.i(TAG, "NFT $nftId already minted (TX: ${mintJob.txHash}) — skipping")
                db.automationJobDao().setDone(job.id, "SUCCESS")
                return
            }
        }

        val nft = db.nftDao().getById(nftId) ?: error("NFT not found")
        val meta = db.metadataDao().getById(nft.metadataId) ?: error("Metadata not found")
        require(meta.metadataUri != null) { "Metadata not yet uploaded for NFT $nftId" }

        // Check wallet session (biometric gate)
        require(walletSession.isSessionValid()) {
            "Wallet session expired — biometric re-authentication required"
        }

        nftStateMachine.transition(nftId, "GENERATED", "MINT_PENDING")

        val tokenUri = meta.metadataUri!!
        val contractAddress = nft.contractAddress ?: error("No contract address on NFT")
        val recipientAddress = db.walletDao().getById(walletId)?.address ?: error("Wallet not found")

        val txResult = polygonRpc.mintNft(
            walletId = walletId,
            contractAddress = contractAddress,
            recipientAddress = recipientAddress,
            tokenUri = tokenUri,
            spendingLimitWei = spendingLimitWeiPerTx
        )

        // Persist TX hash BEFORE waiting for confirmation (crash safety)
        db.mintJobDao().setSubmitted(
            id = job.id, txHash = txResult.txHash, nonce = txResult.nonce.toLong()
        )
        nftStateMachine.transition(nftId, "MINT_PENDING", "MINTING")

        // Enqueue verification job
        db.automationJobDao().insert(AutomationJobEntity(
            id = UUID.randomUUID().toString(),
            jobType = "VERIFY_MINT",
            nftId = nftId,
            idempotencyKey = "VERIFY_MINT_${txResult.txHash}",
            payload = gson.toJson(mapOf("txHash" to txResult.txHash, "walletId" to walletId))
        ))
        db.automationJobDao().setDone(job.id, "SUCCESS")
    }

    private suspend fun handleVerifyMint(job: AutomationJobEntity) {
        val nftId = job.nftId ?: error("No nftId")
        val payload = gson.fromJson(job.payload, Map::class.java)
        val txHash = payload["txHash"]?.toString() ?: error("No txHash")

        val receipt = polygonRpc.waitForReceipt(txHash, pollIntervalMs = 5_000, timeoutMs = 300_000)
            ?: run {
                nftStateMachine.forceState(nftId, "MINT_UNKNOWN")
                error("Mint TX receipt timeout — marked UNKNOWN for manual reconciliation")
            }

        if (!receipt.status) {
            nftStateMachine.forceState(nftId, "MINT_FAILED")
            db.mintJobDao().setError(job.id, "FAILED", "Transaction reverted on-chain")
            db.automationJobDao().setDone(job.id, "FAILED")
            return
        }

        // Get token ID from Transfer event logs (ERC-721 standard)
        val tokenId = extractTokenIdFromReceipt(txHash)
        val nft = db.nftDao().getById(nftId)!!
        val feeEth = org.web3j.utils.Convert.fromWei(
            (java.math.BigInteger.valueOf(receipt.gasUsed) * polygonRpc.getGasPrice()).toBigDecimal(),
            org.web3j.utils.Convert.Unit.ETHER
        ).toPlainString()

        db.nftDao().setMinted(
            nftId, tokenId, txHash,
            receipt.blockNumber, receipt.gasUsed, feeEth
        )
        db.mintJobDao().setComplete(job.id, "SUCCESS", receipt.gasUsed)
        db.collectionDao().incrementMinted(nft.collectionId)

        auditLogger.log("NFT", nftId, "MINT", "MINTING", "MINTED", txHash = txHash)

        Log.i(TAG, "NFT $nftId minted: tokenId=$tokenId TX=$txHash block=${receipt.blockNumber}")
        db.automationJobDao().setDone(job.id, "SUCCESS")
    }

    private suspend fun handleList(job: AutomationJobEntity) {
        val nftId = job.nftId ?: error("No nftId")
        val payload = gson.fromJson(job.payload, Map::class.java)
        val priceWei = payload["priceWei"]?.toString() ?: error("No priceWei")

        val nft = db.nftDao().getById(nftId) ?: error("NFT not found")
        require(nft.state == "MINTED") { "NFT must be MINTED before listing. Current: ${nft.state}" }
        require(nft.tokenId != null) { "Token ID not set" }

        nftStateMachine.forceState(nftId, "LIST_PENDING")

        // Note: Full OpenSea Seaport EIP-712 signing implementation
        // requires building the full OrderComponents struct and signing with the wallet.
        // For complete implementation, see: https://docs.opensea.io/reference/create-listing
        val listingResult = openSeaAdapter.createListing(
            contractAddress = nft.contractAddress!!,
            tokenId = nft.tokenId!!,
            sellerAddress = db.walletDao().getById(nft.walletId)?.address ?: error("Wallet not found"),
            priceWei = priceWei,
            expirationTimestamp = System.currentTimeMillis() / 1000 + 30 * 24 * 3600,
            signedOrderParameters = emptyMap(), // TODO: Build Seaport order params
            signature = "" // TODO: Sign with EIP-712
        )

        val listingId = UUID.randomUUID().toString()
        val priceEth = org.web3j.utils.Convert.fromWei(
            priceWei.toBigDecimal(), org.web3j.utils.Convert.Unit.ETHER
        ).toPlainString()

        db.listingDao().insert(ListingEntity(
            id = listingId,
            nftId = nftId,
            collectionId = nft.collectionId,
            priceWei = priceWei,
            priceEth = priceEth,
            orderHash = listingResult.orderHash.ifBlank { null },
            listingUrl = listingResult.listingUrl.ifBlank { null },
            state = if (listingResult.success) "LISTED" else "LIST_FAILED",
            errorMessage = listingResult.errorMessage
        ))

        if (listingResult.success) {
            db.nftDao().setListed(nftId, listingId)
            db.collectionDao().incrementListed(nft.collectionId)
            auditLogger.log("NFT", nftId, "LIST", "LIST_PENDING", "LISTED")
        } else {
            nftStateMachine.forceState(nftId, "LIST_FAILED")
        }

        db.automationJobDao().setDone(job.id, if (listingResult.success) "SUCCESS" else "FAILED")
    }

    private suspend fun handleVerifyListing(job: AutomationJobEntity) {
        val nftId = job.nftId ?: error("No nftId")
        val nft = db.nftDao().getById(nftId) ?: error("NFT not found")

        val verified = openSeaAdapter.verifyListing(nft.contractAddress ?: "", nft.tokenId ?: "")
        if (!verified) {
            val listing = db.listingDao().getLatestForNft(nftId)
            listing?.let { db.listingDao().setError(it.id, "SYNC_ERROR", "Listing not found on marketplace") }
            nftStateMachine.forceState(nftId, "LIST_UNKNOWN")
        }
        db.automationJobDao().setDone(job.id, "SUCCESS")
    }

    private suspend fun handleMonitorSales(job: AutomationJobEntity) {
        val nftId = job.nftId ?: error("No nftId")
        val nft = db.nftDao().getById(nftId) ?: return

        val sales = openSeaAdapter.getRecentSales(nft.contractAddress ?: "", nft.tokenId ?: "")
        for (sale in sales) {
            val saleId = UUID.randomUUID().toString()
            db.saleDao().insert(SaleEntity(
                id = saleId,
                nftId = nftId,
                listingId = nft.listingId,
                salePrice = sale.salePriceEth,
                buyerAddress = sale.buyerAddress,
                sellerAddress = sale.sellerAddress,
                saleTxHash = sale.txHash,
                state = "DETECTED"
            ))
        }
        db.automationJobDao().setDone(job.id, "SUCCESS")
    }

    // ─── Recovery loop ────────────────────────────────────────────────────────

    private suspend fun runRecoveryLoop() {
        while (isRunning) {
            delay(60_000) // Check every minute
            try {
                reconcileUnknownMints()
                reconcileUnknownListings()
            } catch (e: Exception) {
                Log.e(TAG, "Recovery loop error", e)
            }
        }
    }

    private suspend fun reconcileUnknownMints() {
        val unknownNfts = db.nftDao().getByState("MINT_UNKNOWN")
        for (nft in unknownNfts) {
            val mintJob = db.mintJobDao().getLatestForNft(nft.id) ?: continue
            val txHash = mintJob.txHash ?: continue
            val status = polygonRpc.getTxStatus(txHash)
            Log.i(TAG, "Reconciling MINT_UNKNOWN for NFT ${nft.id}: TX=$txHash status=$status")
            when (status) {
                "CONFIRMED_SUCCESS" -> {
                    val receipt = polygonRpc.waitForReceipt(txHash, timeoutMs = 30_000)
                    receipt?.let {
                        val tokenId = extractTokenIdFromReceipt(txHash)
                        db.nftDao().setMinted(nft.id, tokenId, txHash, it.blockNumber, it.gasUsed, "")
                        db.collectionDao().incrementMinted(nft.collectionId)
                    }
                }
                "CONFIRMED_REVERTED" -> nftStateMachine.forceState(nft.id, "MINT_FAILED")
                "NOT_FOUND"         -> nftStateMachine.forceState(nft.id, "MINT_FAILED")
                // "PENDING", "UNKNOWN" — leave as MINT_UNKNOWN, check next cycle
            }
        }
    }

    private suspend fun reconcileUnknownListings() {
        val unknownNfts = db.nftDao().getByState("LIST_UNKNOWN")
        for (nft in unknownNfts) {
            if (nft.contractAddress == null || nft.tokenId == null) continue
            val exists = openSeaAdapter.verifyListing(nft.contractAddress, nft.tokenId)
            if (exists) {
                val listing = db.listingDao().getLatestForNft(nft.id)
                listing?.let {
                    db.listingDao().setState(it.id, "LISTED")
                    db.nftDao().setListed(nft.id, it.id)
                }
            }
        }
    }

    private suspend fun extractTokenIdFromReceipt(txHash: String): String {
        // Parse Transfer event from receipt logs
        // Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
        return try {
            val web3j = org.web3j.protocol.Web3j.build(
                org.web3j.protocol.http.HttpService(polygonRpc.rpcUrl)
            )
            val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
            receipt.orElse(null)?.logs?.firstOrNull { log ->
                log.topics?.firstOrNull() == "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
            }?.topics?.getOrNull(3)?.let { tokenIdHex ->
                java.math.BigInteger(org.web3j.utils.Numeric.cleanHexPrefix(tokenIdHex), 16).toString()
            } ?: "0"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract token ID from $txHash", e)
            "0"
        }
    }

    private suspend fun buildMetadataJson(meta: com.sadhu.nftautopilot.data.database.entity.NftMetadataEntity): String {
        val attrs = com.google.gson.JsonParser.parseString(meta.attributesJson)
        val json = com.google.gson.JsonObject().apply {
            addProperty("name", meta.tokenName)
            addProperty("description", meta.tokenDescription)
            addProperty("image", meta.imageUri)
            meta.externalUrl?.let { addProperty("external_url", it) }
            add("attributes", attrs)
        }
        return json.toString()
    }
}

// ─── Extension: Seaport listing wired in via SeaportOrderBuilder ──────────────
// AutomationEngine receives SeaportOrderBuilder through Hilt injection.
// The handleList() above is replaced by this production version.
// Swap the stub handleList() body with the real one below when SeaportOrderBuilder
// is injected (add it to the constructor and update the Hilt module).

/*
Real handleList() body once SeaportOrderBuilder is injected:

private suspend fun handleListReal(job: AutomationJobEntity) {
    val nftId    = job.nftId ?: error("No nftId")
    val payload  = gson.fromJson(job.payload, Map::class.java)
    val priceWei = java.math.BigInteger(payload["priceWei"]?.toString() ?: error("No priceWei"))

    val nft    = db.nftDao().getById(nftId)    ?: error("NFT not found")
    val wallet = db.walletDao().getById(nft.walletId) ?: error("Wallet not found")

    require(nft.state == "MINTED")   { "NFT must be MINTED. Current: ${nft.state}" }
    require(nft.tokenId != null)     { "Token ID not set" }
    require(walletSession.isSessionValid()) { "Session expired" }

    nftStateMachine.forceState(nftId, "LIST_PENDING")

    // Build and sign Seaport 1.5 order (EIP-712)
    val signedOrder = seaportOrderBuilder.buildSignedListing(
        walletId        = nft.walletId,
        sellerAddress   = wallet.address,
        contractAddress = nft.contractAddress!!,
        tokenId         = nft.tokenId!!,
        priceWei        = priceWei
    )

    // POST to OpenSea
    val listingResult = openSeaAdapter.createListing(
        contractAddress       = nft.contractAddress!!,
        tokenId               = nft.tokenId!!,
        sellerAddress         = wallet.address,
        priceWei              = priceWei.toString(),
        expirationTimestamp   = System.currentTimeMillis() / 1000 + 30 * 24 * 3600,
        signedOrderParameters = signedOrder.parameters,
        signature             = signedOrder.signature
    )

    val listingId = java.util.UUID.randomUUID().toString()
    val priceEth  = org.web3j.utils.Convert.fromWei(priceWei.toBigDecimal(), org.web3j.utils.Convert.Unit.ETHER).toPlainString()

    db.listingDao().insert(ListingEntity(
        id         = listingId,
        nftId      = nftId,
        collectionId = nft.collectionId,
        priceWei   = priceWei.toString(),
        priceEth   = priceEth,
        orderHash  = listingResult.orderHash.ifBlank { null },
        listingUrl = listingResult.listingUrl.ifBlank { null },
        state      = if (listingResult.success) "LISTED" else "LIST_FAILED",
        errorMessage = listingResult.errorMessage
    ))

    if (listingResult.success) {
        db.nftDao().setListed(nftId, listingId)
        db.collectionDao().incrementListed(nft.collectionId)
        auditLogger.log("NFT", nftId, "LIST", "LIST_PENDING", "LISTED")
    } else {
        nftStateMachine.forceState(nftId, "LIST_FAILED")
    }

    db.automationJobDao().setDone(job.id, if (listingResult.success) "SUCCESS" else "FAILED")
}
*/
