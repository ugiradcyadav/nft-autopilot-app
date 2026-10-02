package com.sadhu.nftautopilot.engine.automation

import android.util.Log
import com.sadhu.nftautopilot.data.database.dao.NftDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Valid NFT states and transitions.
 * UNKNOWN ≠ FAILED. UNKNOWN ≠ SUCCESS.
 * Always reconcile external state before acting on UNKNOWN.
 */
@Singleton
class NftStateMachine @Inject constructor(private val nftDao: NftDao) {
    private val TAG = "NftStateMachine"

    private val validTransitions = mapOf(
        "GENERATED"           to setOf("VALIDATED", "GENERATION_FAILED", "DUPLICATE_REJECTED"),
        "VALIDATED"           to setOf("UPLOAD_PENDING"),
        "UPLOAD_PENDING"      to setOf("UPLOADING", "UPLOAD_FAILED"),
        "UPLOADING"           to setOf("UPLOADED", "UPLOAD_FAILED", "UPLOAD_UNKNOWN"),
        "UPLOADED"            to setOf("MINT_PENDING"),
        "MINT_PENDING"        to setOf("MINTING"),
        "MINTING"             to setOf("MINTED", "MINT_FAILED", "MINT_UNKNOWN"),
        "MINT_UNKNOWN"        to setOf("MINTED", "MINT_FAILED"),
        "MINTED"              to setOf("LIST_PENDING"),
        "LIST_PENDING"        to setOf("LISTING"),
        "LISTING"             to setOf("LISTED", "LIST_FAILED", "LIST_UNKNOWN"),
        "LIST_UNKNOWN"        to setOf("LISTED", "LIST_FAILED"),
        "LISTED"              to setOf("OFFER_RECEIVED", "SOLD", "CANCELLED"),
        "OFFER_RECEIVED"      to setOf("LISTED", "SOLD"),
        "SOLD"                to setOf("VERIFIED_SOLD")
    )

    suspend fun transition(nftId: String, from: String, to: String): Boolean {
        val allowed = validTransitions[from] ?: emptySet()
        if (to !in allowed) {
            Log.w(TAG, "INVALID transition for NFT $nftId: $from → $to (allowed: $allowed)")
            return false
        }
        val updated = nftDao.transitionState(nftId, from, to)
        if (updated == 0) {
            Log.w(TAG, "Transition failed (NFT $nftId not in state $from)")
            return false
        }
        Log.i(TAG, "NFT $nftId: $from → $to")
        return true
    }

    suspend fun forceState(nftId: String, state: String) {
        nftDao.forceState(nftId, state)
        Log.i(TAG, "NFT $nftId forced to: $state")
    }
}
