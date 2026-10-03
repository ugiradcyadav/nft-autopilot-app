package com.sadhu.nftautopilot.data.database.entity

import androidx.room.*

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val label: String,
    val address: String,
    val encryptedKeyBlob: ByteArray,
    val keystoreAlias: String,
    val walletType: String = "HOT",
    val chainId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    override fun equals(other: Any?) = other is WalletEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
    val description: String,
    val contractAddress: String? = null,
    val ownerAddress: String,
    val minterAddress: String,
    val chainId: Long,
    val totalSupply: Int,
    val mintedCount: Int = 0,
    val listedCount: Int = 0,
    val soldCount: Int = 0,
    val royaltyBps: Int = 500,
    val royaltyAddress: String,
    val deployTxHash: String? = null,
    val metadataFrozen: Boolean = false,
    val status: String = "DRAFT",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trait_layers",
    foreignKeys = [ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("collectionId")])
data class TraitLayerEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val name: String,
    val displayOrder: Int,
    val isRequired: Boolean = true
)

@Entity(tableName = "trait_values",
    foreignKeys = [ForeignKey(TraitLayerEntity::class, ["id"], ["layerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("layerId")])
data class TraitValueEntity(
    @PrimaryKey val id: String,
    val layerId: String,
    val collectionId: String,
    val traitType: String,
    val value: String,
    val rarityWeight: Int,
    val assetFileName: String,
    val compatibleWithTraitIds: String = ""
)

@Entity(tableName = "artworks",
    foreignKeys = [ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("collectionId"), Index("sha256Hash")])
data class ArtworkEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val tokenIndex: Int,
    val localFilePath: String,
    val sha256Hash: String,
    val pHashValue: Long,
    val rarityScore: Double,
    val rarityRank: Int = 0,
    val rarityCategory: String = "COMMON",
    val selectedTraitValueIds: String,
    val status: String = "GENERATED"
)

@Entity(tableName = "nft_metadata",
    foreignKeys = [ForeignKey(ArtworkEntity::class, ["id"], ["artworkId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("artworkId")])
data class NftMetadataEntity(
    @PrimaryKey val id: String,
    val artworkId: String,
    val collectionId: String,
    val tokenName: String,
    val tokenDescription: String,
    val externalUrl: String? = null,
    val imageCid: String? = null,
    val imageUri: String? = null,
    val metadataCid: String? = null,
    val metadataUri: String? = null,
    val attributesJson: String,
    val imageUploadStatus: String = "PENDING",
    val metaUploadStatus: String = "PENDING",
    val validationStatus: String = "PENDING",
    val validationError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "nfts",
    foreignKeys = [ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("collectionId"), Index("state")])
data class NftEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val artworkId: String,
    val metadataId: String,
    val walletId: String,
    val tokenId: String? = null,
    val contractAddress: String? = null,
    val chainId: Long,
    val state: String = "GENERATED",
    val mintTxHash: String? = null,
    val mintBlockNumber: Long? = null,
    val mintGasUsed: Long? = null,
    val mintFeeEth: String? = null,
    val mintTimestamp: Long? = null,
    val listingId: String? = null,
    val saleId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mint_jobs",
    indices = [Index("nftId"), Index("state"), Index(value = ["idempotencyKey"], unique = true)])
data class MintJobEntity(
    @PrimaryKey val id: String,
    val nftId: String,
    val collectionId: String,
    val walletId: String,
    val contractAddress: String,
    val tokenUri: String,
    val idempotencyKey: String,
    val state: String = "PENDING",
    val txHash: String? = null,
    val nonce: Long? = null,
    val gasUsed: Long? = null,
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val scheduledAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "listings",
    indices = [Index("nftId"), Index("state")])
data class ListingEntity(
    @PrimaryKey val id: String,
    val nftId: String,
    val collectionId: String,
    val marketplace: String = "OPENSEA",
    val priceWei: String,
    val priceEth: String,
    val orderHash: String? = null,
    val listingUrl: String? = null,
    val state: String = "PENDING",
    val expiresAt: Long? = null,
    val minimumAcceptableOfferWei: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sales",
    indices = [Index("nftId"), Index("state")])
data class SaleEntity(
    @PrimaryKey val id: String,
    val nftId: String,
    val listingId: String? = null,
    val salePrice: String,
    val currency: String = "ETH",
    val buyerAddress: String,
    val sellerAddress: String,
    val saleTxHash: String? = null,
    val saleBlockNumber: Long? = null,
    val marketplaceFeeWei: String? = null,
    val netRevenueWei: String? = null,
    val state: String = "DETECTED",
    val detectedAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null
)

@Entity(tableName = "automation_jobs",
    indices = [Index("jobType"), Index("state"), Index(value = ["idempotencyKey"], unique = true)])
data class AutomationJobEntity(
    @PrimaryKey val id: String,
    val jobType: String,
    val nftId: String? = null,
    val collectionId: String? = null,
    val payload: String = "{}",
    val idempotencyKey: String,
    val state: String = "PENDING",
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val scheduledAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pricing_rules",
    foreignKeys = [ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("collectionId")])
data class PricingRuleEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val strategyType: String = "RARITY",
    val basePriceWei: String,
    val minPriceWei: String,
    val commonMultiplier: Double = 1.0,
    val rareMultiplier: Double = 3.0,
    val epicMultiplier: Double = 10.0,
    val legendaryMultiplier: Double = 40.0,
    val repricingEnabled: Boolean = true,
    val isActive: Boolean = true
)

@Entity(tableName = "audit_logs",
    indices = [Index("entityId"), Index("timestamp")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val entityType: String,
    val entityId: String,
    val operation: String,
    val previousState: String? = null,
    val newState: String? = null,
    val txHash: String? = null,
    val details: String? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "system_settings")
data class SystemSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trait_rules")
data class TraitRuleEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val ruleType: String,
    val triggerTraitValueId: String,
    val targetTraitValueId: String
)
