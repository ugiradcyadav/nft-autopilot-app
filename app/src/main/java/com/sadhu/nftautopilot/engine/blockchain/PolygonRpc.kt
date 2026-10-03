package com.sadhu.nftautopilot.engine.blockchain

import android.util.Log
import com.google.gson.Gson
import com.sadhu.nftautopilot.engine.wallet.NonceManager
import com.sadhu.nftautopilot.engine.wallet.WalletVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

data class TxResult(
    val txHash: String,
    val nonce: BigInteger
)

data class TxReceipt(
    val txHash: String,
    val blockNumber: Long,
    val gasUsed: Long,
    val status: Boolean   // true = success, false = reverted
)

data class NetworkStatus(
    val connected: Boolean,
    val latencyMs: Long,
    val blockNumber: Long?,
    val gasPrice: BigInteger?
)

@Singleton
class PolygonRpcProvider @Inject constructor(
    private val walletVault: WalletVault,
    private val nonceManager: NonceManager
) {
    private val TAG = "PolygonRpc"
    private val gson = Gson()

    // Chain IDs: Polygon mainnet = 137, Amoy testnet = 80002
    var chainId: Long = 80002L
    var rpcUrl: String = "https://rpc-amoy.polygon.technology"
    var polygonScanBaseUrl: String = "https://amoy.polygonscan.com"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val web3j: Web3j get() = Web3j.build(HttpService(rpcUrl, httpClient))

    // ─── Network health ───────────────────────────────────────────────────────

    suspend fun getNetworkStatus(): NetworkStatus = withContext(Dispatchers.IO) {
        return@withContext try {
            val start = System.currentTimeMillis()
            val block = web3j.ethBlockNumber().send()
            val latency = System.currentTimeMillis() - start
            val gasPrice = web3j.ethGasPrice().send().gasPrice
            NetworkStatus(true, latency, block.blockNumber.toLong(), gasPrice)
        } catch (e: Exception) {
            Log.e(TAG, "RPC health check failed", e)
            NetworkStatus(false, -1L, null, null)
        }
    }

    // ─── Balance ─────────────────────────────────────────────────────────────

    suspend fun getBalanceEth(address: String): String = withContext(Dispatchers.IO) {
        val wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().balance
        Convert.fromWei(wei.toBigDecimal(), Convert.Unit.ETHER).toString()
    }

    suspend fun getBalanceWei(address: String): BigInteger = withContext(Dispatchers.IO) {
        web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().balance
    }

    // ─── Nonce seeding ────────────────────────────────────────────────────────

    suspend fun seedNonce(address: String) = withContext(Dispatchers.IO) {
        val pendingNonce = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).send().transactionCount
        nonceManager.seedFromChain(address, pendingNonce)
    }

    // ─── Gas estimation ──────────────────────────────────────────────────────

    suspend fun estimateGas(from: String, to: String, data: String): BigInteger = withContext(Dispatchers.IO) {
        val estimate = web3j.ethEstimateGas(
            Transaction.createFunctionCallTransaction(from, null, null, null, to, data)
        ).send()
        if (estimate.hasError()) {
            Log.w(TAG, "Gas estimate error: ${estimate.error.message}")
            BigInteger.valueOf(200_000L) // Safe fallback
        } else {
            estimate.amountUsed.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN) // +20% buffer
        }
    }

    suspend fun getGasPrice(): BigInteger = withContext(Dispatchers.IO) {
        val price = web3j.ethGasPrice().send().gasPrice
        // Add 10% to ensure inclusion
        price.multiply(BigInteger.valueOf(11)).divide(BigInteger.TEN)
    }

    // ─── Mint Transaction ─────────────────────────────────────────────────────

    /**
     * Builds, signs, and broadcasts the ERC-721 safeMint transaction.
     * Private key is zeroed immediately after signing in WalletVault.withCredentials.
     */
    suspend fun mintNft(
        walletId: String,
        contractAddress: String,
        recipientAddress: String,
        tokenUri: String,
        spendingLimitWei: BigInteger = BigInteger.ZERO
    ): TxResult = withContext(Dispatchers.IO) {

        val fromAddress = walletVault.getAddress(walletId)

        // Verify chain before any signing
        val networkChainId = web3j.ethChainId().send().chainId.toLong()
        require(networkChainId == chainId) {
            "Chain ID mismatch: configured=$chainId, RPC=$networkChainId. Refusing to sign."
        }

        // Seed nonce if needed
        if (!nonceManager.isSeeded(fromAddress)) {
            seedNonce(fromAddress)
        }

        // Encode safeMint(address to, string uri)
        val function = Function(
            "safeMint",
            listOf(
                org.web3j.abi.datatypes.Address(recipientAddress),
                Utf8String(tokenUri)
            ),
            emptyList()
        )
        val encodedData = FunctionEncoder.encode(function)

        // Fee checks
        val gasPrice = getGasPrice()
        val gasLimit = estimateGas(fromAddress, contractAddress, encodedData)
        val estimatedCostWei = gasPrice.multiply(gasLimit)

        if (spendingLimitWei > BigInteger.ZERO && estimatedCostWei > spendingLimitWei) {
            error("Spending limit exceeded: estimated ${estimatedCostWei.toString()} wei, limit ${spendingLimitWei.toString()} wei")
        }

        val balanceWei = getBalanceWei(fromAddress)
        if (balanceWei < estimatedCostWei) {
            error("Insufficient balance: have ${balanceWei.toString()} wei, need ${estimatedCostWei.toString()} wei")
        }

        // Allocate nonce (Mutex-protected, atomic)
        val nonce = nonceManager.getNextNonce(fromAddress)

        // Sign and broadcast — private key zeroed in finally inside withCredentials
        val txHash = walletVault.withCredentials(walletId) { credentials ->
            val rawTxManager = RawTransactionManager(web3j, credentials, chainId)
            val response = rawTxManager.sendTransaction(
                gasPrice, gasLimit, contractAddress, encodedData, BigInteger.ZERO
            )
            if (response.hasError()) {
                nonceManager.reset(fromAddress) // Reset nonce on broadcast failure
                error("Broadcast failed: ${response.error.code} ${response.error.message}")
            }
            response.transactionHash ?: error("Null transaction hash from RPC")
        }

        Log.i(TAG, "Mint TX submitted: $txHash nonce=$nonce")
        TxResult(txHash, nonce)
    }

    // ─── Transaction monitoring ───────────────────────────────────────────────

    suspend fun waitForReceipt(
        txHash: String,
        pollIntervalMs: Long = 5_000L,
        timeoutMs: Long = 300_000L   // 5 min timeout
    ): TxReceipt? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
            if (receipt.isPresent) {
                val r = receipt.get()
                return@withContext TxReceipt(
                    txHash = txHash,
                    blockNumber = r.blockNumber.toLong(),
                    gasUsed = r.gasUsed.toLong(),
                    status = r.isStatusOK
                )
            }
            delay(pollIntervalMs)
        }
        Log.w(TAG, "Receipt timeout for $txHash")
        null
    }

    /** Check if a TX exists and get its status without waiting. */
    suspend fun getTxStatus(txHash: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
            when {
                receipt.isPresent && receipt.get().isStatusOK -> "CONFIRMED_SUCCESS"
                receipt.isPresent && !receipt.get().isStatusOK -> "CONFIRMED_REVERTED"
                else -> {
                    // Check if the TX exists in mempool at all
                    val tx = web3j.ethGetTransactionByHash(txHash).send().transaction
                    if (tx.isPresent) "PENDING" else "NOT_FOUND"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking tx status for $txHash", e)
            "UNKNOWN"
        }
    }

    // ─── Token ownership verification ────────────────────────────────────────

    suspend fun getOwnerOf(contractAddress: String, tokenId: BigInteger): String = withContext(Dispatchers.IO) {
        val function = Function("ownerOf", listOf(Uint256(tokenId)), listOf(object : org.web3j.abi.TypeReference<org.web3j.abi.datatypes.Address>() {}))
        val encoded = FunctionEncoder.encode(function)
        val result = web3j.ethCall(
            Transaction.createEthCallTransaction(null, contractAddress, encoded),
            DefaultBlockParameterName.LATEST
        ).send()
        if (result.hasError()) error("ownerOf failed: ${result.error.message}")
        Numeric.cleanHexPrefix(result.value).takeLast(40).let { "0x$it" }
    }

    // ─── Deploy contract ──────────────────────────────────────────────────────

    suspend fun deployERC721Contract(
        walletId: String,
        name: String,
        symbol: String,
        maxSupply: Int,
        royaltyBps: Int,
        royaltyAddress: String
    ): TxResult = withContext(Dispatchers.IO) {
        val fromAddress = walletVault.getAddress(walletId)

        val networkChainId = web3j.ethChainId().send().chainId.toLong()
        require(networkChainId == chainId) { "Chain ID mismatch during deploy" }

        if (!nonceManager.isSeeded(fromAddress)) seedNonce(fromAddress)

        // ERC-721 bytecode with constructor args
        // Uses standard OpenZeppelin ERC721URIStorage + ERC2981 royalties
        val bytecodeHex = ERC721Bytecode.BYTECODE
        val constructorArgs = encodeConstructorArgs(name, symbol, maxSupply, royaltyBps, royaltyAddress)
        val deployData = bytecodeHex + constructorArgs

        val gasPrice = getGasPrice()
        val gasLimit = BigInteger.valueOf(3_000_000L)
        val nonce = nonceManager.getNextNonce(fromAddress)

        val txHash = walletVault.withCredentials(walletId) { credentials ->
            val rawTxManager = RawTransactionManager(web3j, credentials, chainId)
            val response = rawTxManager.sendTransaction(gasPrice, gasLimit, null, deployData, BigInteger.ZERO)
            if (response.hasError()) {
                nonceManager.reset(fromAddress)
                error("Deploy failed: ${response.error.message}")
            }
            response.transactionHash ?: error("Null tx hash during deploy")
        }
        TxResult(txHash, nonce)
    }

    suspend fun getDeployedContractAddress(txHash: String): String? = withContext(Dispatchers.IO) {
        val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
        receipt.orElse(null)?.contractAddress
    }

    private fun encodeConstructorArgs(name: String, symbol: String, maxSupply: Int, royaltyBps: Int, royaltyAddress: String): String {
        return org.web3j.abi.FunctionEncoder.encodeConstructor(
            listOf(
                Utf8String(name),
                Utf8String(symbol),
                Uint256(maxSupply.toLong()),
                Uint256(royaltyBps.toLong()),
                org.web3j.abi.datatypes.Address(royaltyAddress)
            )
        )
    }

    fun explorerTxUrl(txHash: String) = "$polygonScanBaseUrl/tx/$txHash"
    fun explorerAddressUrl(address: String) = "$polygonScanBaseUrl/address/$address"
    fun explorerTokenUrl(contract: String, tokenId: String) = "$polygonScanBaseUrl/token/$contract?a=$tokenId"
}
