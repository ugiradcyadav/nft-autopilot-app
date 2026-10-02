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
interface TraitDao {
    @Query("SELECT * FROM trait_layers WHERE collectionId = :cid ORDER BY displayOrder ASC")
    suspend fun getLayersForCollection(cid: String): List<TraitLayerEntity>
    @Query("SELECT * FROM trait_values WHERE layerId = :layerId")
    suspend fun getValuesForLayer(layerId: String): List<TraitValueEntity>
    @Query("SELECT * FROM trait_values WHERE id IN (:ids)")
    suspend fun getValuesByIds(ids: List<String>): List<TraitValueEntity>
    @Query("SELECT * FROM trait_rules WHERE collectionId = :cid")
    suspend fun getRulesForCollection(cid: String): List<TraitRuleEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayer(layer: TraitLayerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(value: TraitValueEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: TraitRuleEntity)
}
