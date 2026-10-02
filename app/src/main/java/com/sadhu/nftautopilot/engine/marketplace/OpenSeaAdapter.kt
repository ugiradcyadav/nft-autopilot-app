package com.sadhu.nftautopilot.engine.marketplace

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

// ─── OpenSea API Models ───────────────────────────────────────────────────────

data class OpenSeaNft(
    val identifier: String,
    val collection: String,
    val contract: String,
    val name: String?,
    val description: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("permalink") val permalink: String?
)

data class OpenSeaListing(
    @SerializedName("order_hash") val orderHash: String,
    @SerializedName("chain") val chain: String,
    @SerializedName("type") val type: String,
    @SerializedName("price") val price: OpenSeaPrice?
)

data class OpenSeaPrice(
    val current: OpenSeaPriceCurrent?
)

data class OpenSeaPriceCurrent(
    val currency: String,
    val decimals: Int,
    val value: String
)

data class CreateListingRequest(
    val parameters: Map<String, Any>,
    val signature: String,
    val protocol_address: String = "0x0000000000000068F116a894984e2DB1123eB395" // Seaport 1.5
)

data class ListingResult(
    val orderHash: String,
    val listingUrl: String,
    val success: Boolean,
    val errorMessage: String? = null
)

data class OfferInfo(
    val orderHash: String,
    val priceEth: String,
    val buyerAddress: String,
    val expiresAt: Long
)

data class SaleEvent(
    val orderHash: String,
    val salePriceEth: String,
    val buyerAddress: String,
    val sellerAddress: String,
    val txHash: String?,
    val eventTimestamp: Long
)

// ─── OpenSea Adapter ──────────────────────────────────────────────────────────

@Singleton
class OpenSeaAdapter @Inject constructor() {
    private val TAG = "OpenSeaAdapter"
    private val gson = Gson()

    var apiKey: String = ""          // Set from secure config before use
    var chain: String = "amoy"       // "amoy" for testnet, "matic" for mainnet

    private val baseUrl = "https://api.opensea.io/api/v2"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("X-API-KEY", apiKey)
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    // ─── NFT lookup ───────────────────────────────────────────────────────────

    fun getNft(contractAddress: String, tokenId: String): OpenSeaNft? {
        val request = Request.Builder()
            .url("$baseUrl/chain/$chain/contract/$contractAddress/nfts/$tokenId")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "NFT lookup failed: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val map = gson.fromJson(body, Map::class.java)
                val nft = map["nft"] as? Map<*, *> ?: return null
                OpenSeaNft(
                    identifier = nft["identifier"]?.toString() ?: "",
                    collection = nft["collection"]?.toString() ?: "",
                    contract = nft["contract"]?.toString() ?: "",
                    name = nft["name"]?.toString(),
                    description = nft["description"]?.toString(),
                    imageUrl = nft["image_url"]?.toString(),
                    permalink = nft["opensea_url"]?.toString()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching NFT", e)
            null
        }
    }

    // ─── Create listing (Seaport 1.5) ────────────────────────────────────────

    /**
     * OpenSea listing creation requires:
     * 1. Build Seaport OrderParameters
     * 2. Sign with EIP-712 typed signature
     * 3. POST to /orders/{chain}/seaport/listings
     *
     * The signature is produced by the wallet engine using EIP-712.
     */
    fun createListing(
        contractAddress: String,
        tokenId: String,
        sellerAddress: String,
        priceWei: String,
        expirationTimestamp: Long,
        signedOrderParameters: Map<String, Any>,
        signature: String
    ): ListingResult {
        require(apiKey.isNotBlank()) { "OpenSea API key not configured" }

        val payload = mapOf(
            "parameters" to signedOrderParameters,
            "signature" to signature,
            "protocol_address" to "0x0000000000000068F116a894984e2DB1123eB395"
        )
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/orders/$chain/seaport/listings")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: "{}"
                if (!response.isSuccessful) {
                    Log.e(TAG, "Create listing failed ${response.code}: $responseBody")
                    return ListingResult("", "", false, "HTTP ${response.code}: $responseBody")
                }
                val map = gson.fromJson(responseBody, Map::class.java)
                val orderHash = map["order_hash"]?.toString() ?: ""
                val permalink = "https://opensea.io/assets/$chain/$contractAddress/$tokenId"
                Log.i(TAG, "Listing created: $orderHash")
                ListingResult(orderHash, permalink, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating listing", e)
            ListingResult("", "", false, e.message)
        }
    }

    // ─── Verify listing exists ────────────────────────────────────────────────

    fun verifyListing(contractAddress: String, tokenId: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/listings/collection/${contractAddress.lowercase()}/$tokenId/best")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "Listing verification failed: ${e.message}")
            false
        }
    }

    // ─── Get offers ───────────────────────────────────────────────────────────

    fun getOffers(contractAddress: String, tokenId: String): List<OfferInfo> {
        val request = Request.Builder()
            .url("$baseUrl/offers/collection/${contractAddress.lowercase()}/$tokenId/all?limit=10")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val map = gson.fromJson(body, Map::class.java)
                @Suppress("UNCHECKED_CAST")
                val offers = map["offers"] as? List<Map<String, Any>> ?: return emptyList()
                offers.mapNotNull { offer ->
                    try {
                        OfferInfo(
                            orderHash = offer["order_hash"]?.toString() ?: return@mapNotNull null,
                            priceEth = extractPriceEth(offer),
                            buyerAddress = extractBuyerAddress(offer),
                            expiresAt = (offer["expiration_time"] as? Double)?.toLong() ?: 0L
                        )
                    } catch (e: Exception) { null }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching offers", e)
            emptyList()
        }
    }

    // ─── Get recent sales ─────────────────────────────────────────────────────

    fun getRecentSales(contractAddress: String, tokenId: String): List<SaleEvent> {
        val request = Request.Builder()
            .url("$baseUrl/events/chain/$chain/contract/$contractAddress?token_id=$tokenId&event_type=sale&limit=5")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val map = gson.fromJson(body, Map::class.java)
                @Suppress("UNCHECKED_CAST")
                val events = map["asset_events"] as? List<Map<String, Any>> ?: return emptyList()
                events.mapNotNull { event ->
                    try {
                        SaleEvent(
                            orderHash = event["order_hash"]?.toString() ?: "",
                            salePriceEth = extractPriceEth(event),
                            buyerAddress = extractBuyerAddress(event),
                            sellerAddress = extractSellerAddress(event),
                            txHash = event["transaction"]?.let { (it as? Map<*, *>)?.get("transaction_hash")?.toString() },
                            eventTimestamp = (event["event_timestamp"] as? Double)?.toLong() ?: System.currentTimeMillis() / 1000
                        )
                    } catch (e: Exception) { null }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sales", e)
            emptyList()
        }
    }

    // ─── Collection floor price ───────────────────────────────────────────────

    fun getCollectionFloorEth(collectionSlug: String): String? {
        val request = Request.Builder()
            .url("$baseUrl/collections/$collectionSlug/stats")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val map = gson.fromJson(body, Map::class.java)
                @Suppress("UNCHECKED_CAST")
                val total = map["total"] as? Map<String, Any> ?: return null
                total["floor_price"]?.toString()
            }
        } catch (e: Exception) { null }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun extractPriceEth(map: Map<String, Any>): String {
        val price = map["price"] as? Map<String, Any> ?: return "0"
        val current = price["current"] as? Map<String, Any> ?: return "0"
        return current["value"]?.toString() ?: "0"
    }

    private fun extractBuyerAddress(map: Map<String, Any>): String =
        (map["buyer"] as? Map<*, *>)?.get("address")?.toString() ?: ""

    private fun extractSellerAddress(map: Map<String, Any>): String =
        (map["seller"] as? Map<*, *>)?.get("address")?.toString() ?: ""

    fun nftPermalink(contractAddress: String, tokenId: String) =
        "https://opensea.io/assets/$chain/$contractAddress/$tokenId"
}
