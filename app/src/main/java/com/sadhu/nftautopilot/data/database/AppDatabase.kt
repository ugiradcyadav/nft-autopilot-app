package com.sadhu.nftautopilot.data.database

import android.content.Context
import androidx.room.*
import com.sadhu.nftautopilot.data.database.dao.*
import com.sadhu.nftautopilot.data.database.entity.*

@Database(
    entities = [
        WalletEntity::class,
        CollectionEntity::class,
        TraitLayerEntity::class,
        TraitValueEntity::class,
        TraitRuleEntity::class,
        ArtworkEntity::class,
        NftMetadataEntity::class,
        NftEntity::class,
        MintJobEntity::class,
        ListingEntity::class,
        SaleEntity::class,
        AutomationJobEntity::class,
        PricingRuleEntity::class,
        AuditLogEntity::class,
        SystemSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun collectionDao(): CollectionDao
    abstract fun nftDao(): NftDao

    // TODO: Inhe tab uncomment karein jab aap inke liye .kt files bana lein
    // abstract fun traitDao(): TraitDao
    // abstract fun artworkDao(): ArtworkDao
    // abstract fun metadataDao(): MetadataDao
    // abstract fun mintJobDao(): MintJobDao
    // abstract fun listingDao(): ListingDao
    // abstract fun saleDao(): SaleDao
    // abstract fun automationJobDao(): AutomationJobDao
    // abstract fun auditLogDao(): AuditLogDao
    // abstract fun settingsDao(): SettingsDao

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

// Uncomment when NftSummaryDao is added to @Database entities list
// abstract fun nftSummaryDao(): com.sadhu.nftautopilot.data.database.dao.NftSummaryDao
