package com.sadhu.nftautopilot.engine.wallet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.sadhu.nftautopilot.data.database.dao.WalletDao
import com.sadhu.nftautopilot.data.database.entity.WalletEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WalletVault: Creates and manages encrypted EVM wallets.
 *
 * Security model:
 *  - Private key is encrypted with AES-256-GCM
 *  - The AES key lives in Android Keystore (hardware-backed TEE where available)
 *  - Encrypted blob is stored in Room: [IV(12)] + [ciphertext(32)] + [GCM tag(16)]
 *  - Private key exists in memory ONLY during the signing window
 *  - After signing: Arrays.fill(privateKeyBytes, 0) in finally block
 */
@Singleton
class WalletVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletDao: WalletDao
) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    // ─── Create Wallet ────────────────────────────────────────────────────────

    suspend fun createWallet(label: String, walletType: String, chainId: Long): WalletEntity {
        val id = UUID.randomUUID().toString()
        val keystoreAlias = "nft_wallet_$id"

        // Generate raw private key with cryptographically strong RNG
        val rawPrivateKey = ByteArray(32)
        SecureRandom.getInstanceStrong().nextBytes(rawPrivateKey)

        return try {
            // Derive address from private key
            val keyPair = ECKeyPair.create(rawPrivateKey)
            val address = "0x${Keys.getAddress(keyPair)}"

            // Generate AES key in Android Keystore
            generateKeystoreKey(keystoreAlias)

            // Encrypt private key: produce [IV][ciphertext][GCMtag]
            val encryptedBlob = encryptKey(keystoreAlias, rawPrivateKey)

            val entity = WalletEntity(
                id = id,
                label = label,
                address = address,
                encryptedKeyBlob = encryptedBlob,
                keystoreAlias = keystoreAlias,
                walletType = walletType,
                chainId = chainId
            )
            walletDao.insert(entity)
            entity
        } finally {
            // Zero private key regardless of success or failure
            rawPrivateKey.fill(0)
        }
    }

    // ─── Unlock Credentials (signing window only) ─────────────────────────────

    /**
     * Returns Credentials in the provided [block], then zeros the key.
     * Caller is responsible for NOT storing or leaking the Credentials object.
     */
    suspend fun <T> withCredentials(walletId: String, block: suspend (Credentials) -> T): T {
        val entity = walletDao.getById(walletId)
            ?: error("Wallet $walletId not found")

        val rawPrivateKey = decryptKey(entity.keystoreAlias, entity.encryptedKeyBlob)
        return try {
            val keyPair = ECKeyPair.create(rawPrivateKey)
            val credentials = Credentials.create(keyPair)
            block(credentials)
        } finally {
            rawPrivateKey.fill(0)
        }
    }

    // ─── Address lookup (no key material) ────────────────────────────────────

    suspend fun getAddress(walletId: String): String =
        walletDao.getById(walletId)?.address ?: error("Wallet $walletId not found")

    // ─── AES Keystore helpers ─────────────────────────────────────────────────

    private fun generateKeystoreKey(alias: String) {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Require biometric/PIN before key use; -1 = every use, not time-based
            .setUserAuthenticationRequired(false) // Set true in production after biometric is wired
            .setRandomizedEncryptionRequired(true)
            .build()

        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .also { it.init(spec) }
            .generateKey()
    }

    private fun encryptKey(alias: String, plaintext: ByteArray): ByteArray {
        val secretKey = keyStore.getKey(alias, null) as javax.crypto.SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv                         // 12 bytes GCM IV
        val ciphertext = cipher.doFinal(plaintext) // 32 bytes + 16 byte GCM tag
        return iv + ciphertext                     // [12][48] = 60 bytes total
    }

    private fun decryptKey(alias: String, blob: ByteArray): ByteArray {
        val secretKey = keyStore.getKey(alias, null) as javax.crypto.SecretKey
        val iv = blob.copyOfRange(0, 12)
        val ciphertext = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    // ─── Key deletion (wallet removal) ───────────────────────────────────────

    fun deleteKeystoreKey(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}
