package com.sadhu.nftautopilot.data.database.dao
import androidx.room.Dao
import androidx.room.Query
@Dao
interface SaleDao {
    @Query("SELECT SUM(CAST(netRevenueWei AS REAL)) FROM sales WHERE state = 'VERIFIED'")
    suspend fun getTotalNetRevenueWei(): Double?
}
