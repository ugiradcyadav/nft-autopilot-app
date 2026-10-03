package com.sadhu.nftautopilot.engine.blockchain

import android.util.Log
import com.google.gson.Gson
import com.sadhu.nftautopilot.engine.wallet.WalletVault
import okhttp3.OkHttpClient
import okhttp3.Request
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SeaportOrderBuilder — Full EIP-712 Seaport 1.5 listing creation.
 *
 * Seaport 1.5 protocol address (Polygon mainnet + Amoy):
 *   0x0000000000000068F116a894984e2DB1123eB395
 *
 * OpenSea conduit key:
 *   0x0000007b02230091a7ed01230072f7006a004d60a8d4e71d599b8104250f0000
 *
 * OpenSea fee recipient (Polygon):
 *   0x0000a26b00c1F0DF003000390027140000fAa719
 *
 * EIP-712 type hashes are computed from canonical string encodings
 * per the Seaport 1.5 spec.
 */
@Singleton
class SeaportOrderBuilder @Inject constructor(
    private val walletVault: WalletVault,
    private val gson: Gson
) {
    private val TAG = "SeaportOrderBuilder"

    // ─── Seaport constants ────────────────────────────────────────────────────

    companion object {
        const val SEAPORT_ADDRESS  = "0x0000000000000068F116a894984e2DB1123eB395"
        const val CONDUIT_KEY      = "0x0000007b02230091a7ed01230072f7006a004d60a8d4e71d599b8104250f0000"
        const val OPENSEA_FEE_RECIPIENT_POLYGON = "0x0000a26b00c1F0DF003000390027140000fAa719"
        const val OPENSEA_FEE_BPS  = 250 // 2.5%

        // Item types
        const val ITEM_NATIVE  = 0  // MATIC
        const val ITEM_ERC721  = 2

        // Order type: FULL_OPEN = 0
        const val ORDER_FULL_OPEN = 0

        // EIP-712 type strings (canonical Seaport 1.5 encoding)
        private const val DOMAIN_TYPE_STRING =
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"

        private const val OFFER_ITEM_TYPE_STRING =
            "OfferItem(uint8 itemType,address token,uint256 identifierOrCriteria,uint256 startAmount,uint256 endAmount)"

        private const val CONSIDERATION_ITEM_TYPE_STRING =
            "ConsiderationItem(uint8 itemType,address token,uint256 identifierOrCriteria,uint256 startAmount,uint256 endAmount,address recipient)"

        // Note: dependency types appended in alphabetical order per EIP-712 spec
        private const val ORDER_COMPONENTS_TYPE_STRING =
            "OrderComponents(address offerer,address zone,OfferItem[] offer,ConsiderationItem[] consideration,uint8 orderType,uint256 startTime,uint256 endTime,bytes32 zoneHash,uint256 salt,bytes32 conduitKey,uint256 counter)" +
            "ConsiderationItem(uint8 itemType,address token,uint256 identifierOrCriteria,uint256 startAmount,uint256 endAmount,address recipient)" +
            "OfferItem(uint8 itemType,address token,uint256 identifierOrCriteria,uint256 startAmount,uint256 endAmount)"

        // Pre-computed type hashes (keccak256 of type strings)
        val DOMAIN_TYPE_HASH: ByteArray           = Hash.sha3(DOMAIN_TYPE_STRING.toByteArray())
        val OFFER_ITEM_TYPE_HASH: ByteArray        = Hash.sha3(OFFER_ITEM_TYPE_STRING.toByteArray())
        val CONSIDERATION_ITEM_TYPE_HASH: ByteArray= Hash.sha3(CONSIDERATION_ITEM_TYPE_STRING.toByteArray())
        val ORDER_COMPONENTS_TYPE_HASH: ByteArray  = Hash.sha3(ORDER_COMPONENTS_TYPE_STRING.toByteArray())
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    var apiKey: String = ""
    var chainId: Long = 80002L
    var openSeaApiChain: String = "amoy" // "amoy" or "matic"

    // ─── Build and sign a listing order ──────────────────────────────────────

    data class SignedOrder(
        val parameters: Map<String, Any>,
        val signature: String
    )

    /**
     * Builds and signs a Seaport 1.5 ERC-721 listing order.
     *
     * @param walletId       ID of the seller's wallet in WalletVault
     * @param sellerAddress  Seller's Ethereum address (must match wallet)
     * @param contractAddress ERC-721 contract address
     * @param tokenId        Token ID to list
     * @param priceWei       Listing price in wei (total, including fees)
     * @param durationSecs   Listing duration in seconds (default 30 days)
     */
    suspend fun buildSignedListing(
        walletId: String,
        sellerAddress: String,
        contractAddress: String,
        tokenId: String,
        priceWei: BigInteger,
        durationSecs: Long = 30L * 24 * 3600
    ): SignedOrder {
        // 1. Fetch counter from OpenSea (replay protection)
        val counter = fetchCounter(sellerAddress)
        Log.i(TAG, "OpenSea counter for $sellerAddress = $counter")

        // 2. Compute fee split
        val feeBps = OPENSEA_FEE_BPS.toLong()
        val feeWei = priceWei.toLong().let { p -> (p * feeBps) / 10_000 }.let { BigInteger.valueOf(it) }
        val sellerWei = priceWei - feeWei

        val now = System.currentTimeMillis() / 1000L
        val expiry = now + durationSecs
        val salt = BigInteger(128, java.security.SecureRandom.getInstanceStrong())

        // 3. Build OrderComponents
        val offerItems = listOf(
            mapOf(
                "itemType"              to ITEM_ERC721,
                "token"                 to contractAddress,
                "identifierOrCriteria"  to tokenId,
                "startAmount"           to "1",
                "endAmount"             to "1"
            )
        )

        val considerationItems = listOf(
            // Seller receives their cut
            mapOf(
                "itemType"              to ITEM_NATIVE,
                "token"                 to "0x0000000000000000000000000000000000000000",
                "identifierOrCriteria"  to "0",
                "startAmount"           to sellerWei.toString(),
                "endAmount"             to sellerWei.toString(),
                "recipient"             to sellerAddress
            ),
            // OpenSea fee
            mapOf(
                "itemType"              to ITEM_NATIVE,
                "token"                 to "0x0000000000000000000000000000000000000000",
                "identifierOrCriteria"  to "0",
                "startAmount"           to feeWei.toString(),
                "endAmount"             to feeWei.toString(),
                "recipient"             to OPENSEA_FEE_RECIPIENT_POLYGON
            )
        )

        val orderComponents = mapOf(
            "offerer"           to sellerAddress,
            "zone"              to "0x0000000000000000000000000000000000000000",
            "offer"             to offerItems,
            "consideration"     to considerationItems,
            "orderType"         to ORDER_FULL_OPEN,
            "startTime"         to now.toString(),
            "endTime"           to expiry.toString(),
            "zoneHash"          to "0x0000000000000000000000000000000000000000000000000000000000000000",
            "salt"              to salt.toString(),
            "conduitKey"        to CONDUIT_KEY,
            "counter"           to counter.toString()
        )

        // 4. Compute EIP-712 digest
        val domainSeparator = computeDomainSeparator()
        val structHash       = computeOrderStructHash(
            sellerAddress, offerItems, considerationItems,
            now, expiry, salt, counter
        )
        val digest = computeDigest(domainSeparator, structHash)

        // 5. Sign digest — private key zeroed immediately after in WalletVault
        val signature = walletVault.withCredentials(walletId) { credentials ->
            val sig = Sign.signMessage(digest, credentials.ecKeyPair, false)
            // Encode as 65-byte r+s+v hex
            val r = Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, sig.r), 64)
            val s = Numeric.toHexStringNoPrefixZeroPadded(BigInteger(1, sig.s), 64)
            val v = Integer.toHexString(sig.v.toString().toIntOrNull() ?: 0).padStart(2, '0')
            "0x$r$s$v"
        }

        Log.i(TAG, "Order signed. Signature: ${signature.take(20)}…")

        return SignedOrder(
            parameters = orderComponents,
            signature  = signature
        )
    }

    // ─── EIP-712 hashing ─────────────────────────────────────────────────────

    private fun computeDomainSeparator(): ByteArray {
        return Hash.sha3(
            encodeAbi(
                bytes32(DOMAIN_TYPE_HASH),
                bytes32(Hash.sha3("Seaport".toByteArray())),
                bytes32(Hash.sha3("1.5".toByteArray())),
                uint256(BigInteger.valueOf(chainId)),
                address(SEAPORT_ADDRESS)
            )
        )
    }

    private fun computeOrderStructHash(
        offerer: String,
        offerItems: List<Map<String, Any>>,
        considerationItems: List<Map<String, Any>>,
        startTime: Long,
        endTime: Long,
        salt: BigInteger,
        counter: BigInteger
    ): ByteArray {

        val offerHash = hashOfferItems(offerItems)
        val considerationHash = hashConsiderationItems(considerationItems)

        return Hash.sha3(
            encodeAbi(
                bytes32(ORDER_COMPONENTS_TYPE_HASH),
                address(offerer),
                address("0x0000000000000000000000000000000000000000"),  // zone
                bytes32(offerHash),
                bytes32(considerationHash),
                uint256(BigInteger.valueOf(ORDER_FULL_OPEN.toLong())),  // orderType
                uint256(BigInteger.valueOf(startTime)),
                uint256(BigInteger.valueOf(endTime)),
                bytes32(ByteArray(32)),  // zoneHash
                uint256(salt),
                bytes32(Numeric.hexStringToByteArray(CONDUIT_KEY.removePrefix("0x"))),
                uint256(counter)
            )
        )
    }

    private fun hashOfferItems(items: List<Map<String, Any>>): ByteArray {
        val encodedItems = items.map { item ->
            Hash.sha3(
                encodeAbi(
                    bytes32(OFFER_ITEM_TYPE_HASH),
                    uint256(BigInteger.valueOf((item["itemType"] as Int).toLong())),
                    address(item["token"] as String),
                    uint256(BigInteger(item["identifierOrCriteria"] as String)),
                    uint256(BigInteger(item["startAmount"] as String)),
                    uint256(BigInteger(item["endAmount"] as String))
                )
            )
        }
        return Hash.sha3(encodedItems.fold(ByteArray(0)) { acc, b -> acc + b })
    }

    private fun hashConsiderationItems(items: List<Map<String, Any>>): ByteArray {
        val encodedItems = items.map { item ->
            Hash.sha3(
                encodeAbi(
                    bytes32(CONSIDERATION_ITEM_TYPE_HASH),
                    uint256(BigInteger.valueOf((item["itemType"] as Int).toLong())),
                    address(item["token"] as String),
                    uint256(BigInteger(item["identifierOrCriteria"] as String)),
                    uint256(BigInteger(item["startAmount"] as String)),
                    uint256(BigInteger(item["endAmount"] as String)),
                    address(item["recipient"] as String)
                )
            )
        }
        return Hash.sha3(encodedItems.fold(ByteArray(0)) { acc, b -> acc + b })
    }

    /** EIP-712 final digest: keccak256("\x19\x01" + domainSeparator + structHash) */
    private fun computeDigest(domainSeparator: ByteArray, structHash: ByteArray): ByteArray {
        val prefix = byteArrayOf(0x19, 0x01)
        return Hash.sha3(prefix + domainSeparator + structHash)
    }

    // ─── ABI encoding helpers (fixed 32-byte slots) ───────────────────────────

    private fun encodeAbi(vararg slots: ByteArray): ByteArray =
        slots.fold(ByteArray(0)) { acc, slot -> acc + slot }

    private fun bytes32(data: ByteArray): ByteArray {
        require(data.size <= 32) { "bytes32 overflow: ${data.size} bytes" }
        val out = ByteArray(32)
        data.copyInto(out, 32 - data.size)
        return out
    }

    private fun uint256(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        val out = ByteArray(32)
        val start = maxOf(0, bytes.size - 32)
        val len   = minOf(bytes.size, 32)
        bytes.copyInto(out, 32 - len, start, start + len)
        return out
    }

    private fun address(addr: String): ByteArray {
        val clean = Numeric.cleanHexPrefix(addr)
        val bytes = Numeric.hexStringToByteArray(clean.padStart(40, '0'))
        val out = ByteArray(32)
        bytes.copyInto(out, 12)
        return out
    }

    // ─── Fetch counter from OpenSea ───────────────────────────────────────────

    private fun fetchCounter(offererAddress: String): BigInteger {
        return try {
            val url = "https://api.opensea.io/api/v2/orders/$openSeaApiChain/seaport/counters/$offererAddress"
            val req = Request.Builder()
                .url(url)
                .header("X-API-KEY", apiKey)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Counter fetch failed ${resp.code} — defaulting to 0")
                    return BigInteger.ZERO
                }
                val body = resp.body?.string() ?: return BigInteger.ZERO
                val map  = gson.fromJson(body, Map::class.java)
                val counterVal = map["counter"]?.toString() ?: "0"
                BigInteger(counterVal)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching counter: ${e.message}")
            BigInteger.ZERO
        }
    }
}
