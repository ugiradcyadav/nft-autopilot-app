package com.sadhu.nftautopilot.data.database.dao
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface AutomationJobDao {
    @Query("SELECT COUNT(*) FROM automation_jobs WHERE state = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}
