package com.sadhu.nftautopilot.util

import com.sadhu.nftautopilot.data.database.dao.AuditLogDao
import com.sadhu.nftautopilot.data.database.entity.AuditLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(private val auditLogDao: AuditLogDao) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun log(
        entityType: String,
        entityId: String,
        operation: String,
        previousState: String? = null,
        newState: String? = null,
        txHash: String? = null,
        details: String? = null,
        errorMessage: String? = null
    ) {
        scope.launch {
            auditLogDao.insert(AuditLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                previousState = previousState,
                newState = newState,
                txHash = txHash,
                details = details,
                errorMessage = errorMessage
            ))
        }
    }
}
