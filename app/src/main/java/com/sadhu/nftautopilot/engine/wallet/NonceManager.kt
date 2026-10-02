package com.sadhu.nftautopilot.engine.wallet

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NonceManager: Serializes EVM nonce allocation across concurrent coroutines.
 *
 * EVM nonce rules:
 *  - Two txs with the same nonce → only one confirms, other is dropped (silent loss)
 *  - Gap in nonce sequence → all subsequent txs stall in mempool
 *  - Solution: single Mutex-protected counter, reset from chain on startup/error
 */
@Singleton
class NonceManager @Inject constructor() {
    private val mutex = Mutex()
    private val walletNonces = mutableMapOf<String, BigInteger>() // address -> next nonce
    private val TAG = "NonceManager"

    /** Seed the nonce for an address from chain state. Must be called on startup and after resets. */
    suspend fun seedFromChain(address: String, chainNonce: BigInteger) = mutex.withLock {
        walletNonces[address.lowercase()] = chainNonce
        Log.i(TAG, "Nonce seeded for $address = $chainNonce")
    }

    /** Atomically reserve and return the next nonce. */
    suspend fun getNextNonce(address: String): BigInteger = mutex.withLock {
        val key = address.lowercase()
        val current = walletNonces[key] ?: error("Nonce not seeded for $address — call seedFromChain first")
        walletNonces[key] = current.add(BigInteger.ONE)
        Log.i(TAG, "Nonce allocated for $address = $current")
        current
    }

    /** Reset nonce (call after RPC error, app restart, or detected nonce gap). */
    suspend fun reset(address: String) = mutex.withLock {
        walletNonces.remove(address.lowercase())
        Log.w(TAG, "Nonce reset for $address — will re-seed from chain before next tx")
    }

    fun isSeeded(address: String): Boolean =
        walletNonces.containsKey(address.lowercase())
}
