package com.sadhu.nftautopilot.data.database.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "audit_logs")
data class AuditLogEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val message: String)
