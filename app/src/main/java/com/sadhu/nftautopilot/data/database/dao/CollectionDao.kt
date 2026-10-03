package com.sadhu.nftautopilot.data.database.dao

import androidx.room.*
import com.sadhu.nftautopilot.data.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CollectionEntity>>
    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(c: CollectionEntity)
    @Update
    suspend fun update(c: CollectionEntity)
    @Query("UPDATE collections SET mintedCount = mintedCount + 1, updatedAt = :ts WHERE id = :id")
    suspend fun incrementMinted(id: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE collections SET listedCount = listedCount + 1, updatedAt = :ts WHERE id = :id")
    suspend fun incrementListed(id: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE collections SET soldCount = soldCount + 1, updatedAt = :ts WHERE id = :id")
    suspend fun incrementSold(id: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE collections SET contractAddress = :addr, deployTxHash = :txHash, status = 'DEPLOYED', updatedAt = :ts WHERE id = :id")
    suspend fun setDeployed(id: String, addr: String, txHash: String, ts: Long = System.currentTimeMillis())
}

@Dao
