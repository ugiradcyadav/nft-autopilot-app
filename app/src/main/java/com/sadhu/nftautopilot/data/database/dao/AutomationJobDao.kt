package com.sadhu.nftautopilot.data.database.dao
import androidx.room.*
import com.sadhu.nftautopilot.data.database.entity.AutomationJobEntity
import kotlinx.coroutines.flow.Flow
@Dao interface AutomationJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(job: AutomationJobEntity)
    @Query("SELECT * FROM automation_jobs WHERE state IN ('PENDING', 'RETRY') ORDER BY scheduledAt ASC LIMIT :limit") suspend fun getNextPending(limit: Int = 10): List<AutomationJobEntity>
    @Query("SELECT * FROM automation_jobs WHERE state = 'RUNNING'") suspend fun getRunning(): List<AutomationJobEntity>
    @Query("UPDATE automation_jobs SET state = :s, startedAt = :ts, updatedAt = :ts WHERE id = :id") suspend fun setRunning(id: String, s: String = "RUNNING", ts: Long = System.currentTimeMillis())
    @Query("UPDATE automation_jobs SET state = :s, completedAt = :ts, updatedAt = :ts WHERE id = :id") suspend fun setDone(id: String, s: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE automation_jobs SET state = :s, errorMessage = :err, retryCount = retryCount + 1, updatedAt = :ts WHERE id = :id") suspend fun setError(id: String, s: String, err: String, ts: Long = System.currentTimeMillis())
    @Query("SELECT COUNT(*) FROM automation_jobs WHERE state = 'PENDING'") fun observePendingCount(): Flow<Int>
}
