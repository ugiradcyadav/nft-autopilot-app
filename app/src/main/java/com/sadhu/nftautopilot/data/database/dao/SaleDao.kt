package com.sadhu.nftautopilot.data.database.dao
import androidx.room.Dao
import androidx.room.Query
@Dao
interface SaleDao {
    @Query("SELECT SUM(netRevenueWei) FROM sales")
    fun getTotalNetRevenueWei(): Double?
}
