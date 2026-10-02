package com.sadhu.nftautopilot.data.database.dao

import androidx.room.*
import com.sadhu.nftautopilot.data.database.entity.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE isActive = 1")
    fun observeAll(): Flow<List<WalletEntity>>
    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getById(id: String): WalletEntity?
    @Query("SELECT * FROM wallets WHERE walletType = 'HOT' AND isActive = 1 LIMIT 1")
    suspend fun getActiveHotWallet(): WalletEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wallet: WalletEntity)
    @Update
    suspend fun update(wallet: WalletEntity)
    @Query("UPDATE wallets SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}
