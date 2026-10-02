package com.sadhu.nftautopilot.data.database

import android.content.Context
import androidx.room.*
import com.sadhu.nftautopilot.data.database.dao.*
import com.sadhu.nftautopilot.data.database.entity.*

@Database(
    entities = [
        WalletEntity::class,
        CollectionEntity::class,
        NftEntity::class,
        SaleEntity::class,
        AutomationJobEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun collectionDao(): CollectionDao
    abstract fun nftDao(): NftDao
    abstract fun saleDao(): SaleDao
    abstract fun automationJobDao(): AutomationJobDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nft_autopilot.db"
                )
                .enableWriteAheadLogging()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build().also { INSTANCE = it }
            }
    }
}
