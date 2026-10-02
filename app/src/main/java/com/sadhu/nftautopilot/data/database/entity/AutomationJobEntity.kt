package com.sadhu.nftautopilot.data.database.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "automation_jobs")
data class AutomationJobEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val status: String)
