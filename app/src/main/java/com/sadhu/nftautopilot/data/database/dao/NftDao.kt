package com.sadhu.nftautopilot.data.database.dao
import androidx.room.Dao
import androidx.room.Query
import com.sadhu.nftautopilot.data.database.entity.NftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NftDao {
    @Query("SELECT COUNT(*) FROM nfts WHERE state = :s")
    suspend fun countByState(s: String): Int
}
