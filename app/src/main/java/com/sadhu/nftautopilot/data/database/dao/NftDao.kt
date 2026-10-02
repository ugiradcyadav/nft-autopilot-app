package com.sadhu.nftautopilot.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sadhu.nftautopilot.data.database.entity.NftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NftDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(nft: NftEntity)

    @Query("SELECT * FROM nfts WHERE id = :id")
    suspend fun getById(id: String): NftEntity?

    @Query("SELECT * FROM nfts WHERE collectionId = :cid ORDER BY createdAt ASC")
    fun observeByCollection(cid: String): Flow<List<NftEntity>>

    @Query("SELECT * FROM nfts WHERE state = :state")
    suspend fun getByState(state: String): List<NftEntity>

    @Query("UPDATE nfts SET state = :newState, updatedAt = :ts WHERE id = :id AND state = :expectedState")
    suspend fun transitionState(id: String, expectedState: String, newState: String, ts: Long = System.currentTimeMillis()): Int

    @Query("UPDATE nfts SET state = :s, updatedAt = :ts WHERE id = :id")
    suspend fun forceState(id: String, s: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE nfts SET tokenId = :tokenId, mintTxHash = :txHash, mintBlockNumber = :block, mintGasUsed = :gas, mintFeeEth = :fee, mintTimestamp = :ts, state = 'MINTED', updatedAt = :ts WHERE id = :id")
    suspend fun setMinted(id: String, tokenId: String, txHash: String, block: Long, gas: Long, fee: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE nfts SET listingId = :listingId, state = 'LISTED', updatedAt = :ts WHERE id = :id")
    suspend fun setListed(id: String, listingId: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE nfts SET saleId = :saleId, state = 'SOLD', updatedAt = :ts WHERE id = :id")
    suspend fun setSold(id: String, saleId: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM nfts WHERE collectionId = :cid AND state = :s")
    suspend fun countByState(cid: String, s: String): Int
}
