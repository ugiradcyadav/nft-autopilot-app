package com.sadhu.nftautopilot.engine.ipfs

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PinataResponse(
    @SerializedName("IpfsHash") val ipfsHash: String,
    @SerializedName("PinSize") val pinSize: Long,
    @SerializedName("Timestamp") val timestamp: String
)

data class UploadResult(
    val cid: String,
    val uri: String,          // ipfs://CID
    val gatewayUrl: String    // https://gateway.pinata.cloud/ipfs/CID
)

@Singleton
class IpfsStorageEngine @Inject constructor() {
    private val TAG = "IpfsStorage"
    private val gson = Gson()

    var pinataJwt: String = ""   // Set from secure settings before use
    private val gateway = "https://gateway.pinata.cloud/ipfs"
    private val pinFileUrl = "https://api.pinata.cloud/pinning/pinFileToIPFS"
    private val pinJsonUrl = "https://api.pinata.cloud/pinning/pinJSONToIPFS"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // ─── Upload image file ────────────────────────────────────────────────────

    suspend fun uploadImage(imageFile: File, nftName: String): UploadResult {
        require(pinataJwt.isNotBlank()) { "Pinata JWT not configured" }
        require(imageFile.exists()) { "Image file not found: ${imageFile.absolutePath}" }

        val mimeType = when (imageFile.extension.lowercase()) {
            "png"  -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else   -> "application/octet-stream"
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", imageFile.name, imageFile.asRequestBody(mimeType.toMediaType()))
            .addFormDataPart("pinataMetadata", gson.toJson(mapOf("name" to "NFT-$nftName-image")))
            .addFormDataPart("pinataOptions", gson.toJson(mapOf("cidVersion" to 1)))
            .build()

        val request = Request.Builder()
            .url(pinFileUrl)
            .header("Authorization", "Bearer $pinataJwt")
            .post(requestBody)
            .build()

        return executeUpload(request, "image")
    }

    // ─── Upload JSON metadata ─────────────────────────────────────────────────

    suspend fun uploadMetadata(metadataJson: String, nftName: String): UploadResult {
        require(pinataJwt.isNotBlank()) { "Pinata JWT not configured" }

        val body = mapOf(
            "pinataContent" to gson.fromJson(metadataJson, Map::class.java),
            "pinataMetadata" to mapOf("name" to "NFT-$nftName-metadata"),
            "pinataOptions" to mapOf("cidVersion" to 1)
        )
        val requestBody = gson.toJson(body).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(pinJsonUrl)
            .header("Authorization", "Bearer $pinataJwt")
            .post(requestBody)
            .build()

        return executeUpload(request, "metadata")
    }

    // ─── Verify CID is accessible ────────────────────────────────────────────

    suspend fun verifyCid(cid: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$gateway/$cid")
                .head() // HEAD request — just check it exists
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.w(TAG, "CID verification failed for $cid: ${e.message}")
            false
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private fun executeUpload(request: Request, type: String): UploadResult {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: "no body"
                error("Pinata $type upload failed ${response.code}: $body")
            }
            val responseBody = response.body?.string() ?: error("Empty response from Pinata")
            val pinata = gson.fromJson(responseBody, PinataResponse::class.java)
            val cid = pinata.ipfsHash
            Log.i(TAG, "$type uploaded: CID=$cid size=${pinata.pinSize}")
            return UploadResult(
                cid = cid,
                uri = "ipfs://$cid",
                gatewayUrl = "$gateway/$cid"
            )
        }
    }

    fun buildTokenUri(metadataCid: String) = "ipfs://$metadataCid"
    fun buildGatewayUrl(cid: String) = "$gateway/$cid"
}
