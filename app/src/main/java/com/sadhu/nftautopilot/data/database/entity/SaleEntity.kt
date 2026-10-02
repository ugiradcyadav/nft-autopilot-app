package com.sadhu.nftautopilot.data.database.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "sales")
data class SaleEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val netRevenueWei: Double)
