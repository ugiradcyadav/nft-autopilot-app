package com.sadhu.nftautopilot.di

import android.content.Context
import com.google.gson.Gson
import com.sadhu.nftautopilot.data.database.AppDatabase
import com.sadhu.nftautopilot.data.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideGson(): Gson = Gson()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        AppDatabase.getInstance(ctx)

    @Provides @Singleton fun provideWalletDao(db: AppDatabase): WalletDao = db.walletDao()
    @Provides @Singleton fun provideCollectionDao(db: AppDatabase): CollectionDao = db.collectionDao()
    @Provides @Singleton fun provideTraitDao(db: AppDatabase): TraitDao = db.traitDao()
    @Provides @Singleton fun provideArtworkDao(db: AppDatabase): ArtworkDao = db.artworkDao()
    @Provides @Singleton fun provideMetadataDao(db: AppDatabase): MetadataDao = db.metadataDao()
    @Provides @Singleton fun provideNftDao(db: AppDatabase): NftDao = db.nftDao()
    @Provides @Singleton fun provideMintJobDao(db: AppDatabase): MintJobDao = db.mintJobDao()
    @Provides @Singleton fun provideListingDao(db: AppDatabase): ListingDao = db.listingDao()
    @Provides @Singleton fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
    @Provides @Singleton fun provideAutomationJobDao(db: AppDatabase): AutomationJobDao = db.automationJobDao()
    @Provides @Singleton fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()
    @Provides @Singleton fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
