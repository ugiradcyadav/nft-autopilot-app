package com.sadhu.nftautopilot.data.database.dao
import androidx.room.Dao
import androidx.room.Query
import com.sadhu.nftautopilot.data.database.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>
}
