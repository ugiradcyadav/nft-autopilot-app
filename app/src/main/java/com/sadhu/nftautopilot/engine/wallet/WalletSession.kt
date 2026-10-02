package com.sadhu.nftautopilot.engine.wallet

import javax.inject.Inject
import javax.inject.Singleton

/**
 * WalletSession: Resolves the biometric vs full-auto conflict.
 *
 * Biometric per-transaction is incompatible with unattended bulk minting.
 * Solution: one biometric authentication starts a session for SESSION_DURATION_MS.
 * Full-auto operates within active sessions. Session expires → automation pauses
 * until user re-authenticates.
 */
@Singleton
class WalletSession @Inject constructor() {
    private var sessionExpiry: Long = 0L
    private var sessionWalletId: String? = null

    /** Session duration — user-configurable; default 30 minutes. */
    var sessionDurationMs: Long = 30 * 60 * 1_000L

    fun startSession(walletId: String) {
        sessionWalletId = walletId
        sessionExpiry = System.currentTimeMillis() + sessionDurationMs
    }

    fun isSessionValid(): Boolean =
        sessionWalletId != null && System.currentTimeMillis() < sessionExpiry

    fun getSessionWalletId(): String? =
        if (isSessionValid()) sessionWalletId else null

    fun invalidate() {
        sessionExpiry = 0L
        sessionWalletId = null
    }

    fun extendSession() {
        if (isSessionValid()) sessionExpiry = System.currentTimeMillis() + sessionDurationMs
    }

    fun remainingMs(): Long = maxOf(0L, sessionExpiry - System.currentTimeMillis())
}
