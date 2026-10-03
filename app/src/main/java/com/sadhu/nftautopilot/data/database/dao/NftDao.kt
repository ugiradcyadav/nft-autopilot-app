package com.sadhu.nftautopilot.data.database.dao
import androidx.room.*
import com.sadhu.nftautopilot.data.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao interface ArtworkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(a: ArtworkEntity)
    @Query("SELECT * FROM artworks WHERE collectionId = :cid ORDER BY tokenIndex ASC") suspend fun getByCollection(cid: String): List<ArtworkEntity>
    @Query("SELECT COUNT(*) FROM artworks WHERE sha256Hash = :hash") suspend fun countByHash(hash: String): Int
    @Query("SELECT pHashValue FROM artworks WHERE collectionId = :cid") suspend fun getPHashValues(cid: String): List<Long>
    @Query("UPDATE artworks SET rarityScore = :score, rarityRank = :rank, rarityCategory = :cat WHERE id = :id") suspend fun updateRarity(id: String, score: Double, rank: Int, cat: String)
}

@Dao interface MetadataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(m: NftMetadataEntity)
    @Query("SELECT * FROM nft_metadata WHERE id = :id") suspend fun getById(id: String): NftMetadataEntity?
    @Query("UPDATE nft_metadata SET imageCid = :cid, imageUri = :uri, imageUploadStatus = 'UPLOADED', updatedAt = :ts WHERE id = :id") suspend fun setImageUploaded(id: String, cid: String, uri: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE nft_metadata SET metadataCid = :cid, metadataUri = :uri, metaUploadStatus = 'UPLOADED', updatedAt = :ts WHERE id = :id") suspend fun setMetaUploaded(id: String, cid: String, uri: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE nft_metadata SET imageUploadStatus = :s, updatedAt = :ts WHERE id = :id") suspend fun setImageStatus(id: String, s: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE nft_metadata SET metaUploadStatus = :s, updatedAt = :ts WHERE id = :id") suspend fun setMetaStatus(id: String, s: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE nft_metadata SET validationStatus = :s, validationError = :err, updatedAt = :ts WHERE id = :id") suspend fun setValidationResult(id: String, s: String, err: String?, ts: Long = System.currentTimeMillis())
}

@Dao interface NftDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(nft: NftEntity)
    @Query("SELECT * FROM nfts WHERE id = :id") suspend fun getById(id: String): NftEntity?
    @Query("SELECT * FROM nfts WHERE collectionId = :cid ORDER BY createdAt ASC") fun observeByCollection(cid: String): Flow<List<NftEntity>>
    @Query("SELECT * FROM nfts WHERE state = :state") suspend fun getByState(state: String): List<NftEntity>
    @Query("UPDATE nfts SET state = :newState, updatedAt = :ts WHERE id = :id AND state = :expectedState") suspend fun transitionState(id: String, expectedState: String, newState: String, ts: Long = System.currentTimeMillis()): Int
    @Query("UPDATE nfts SET state = :s, updatedAt = :ts WHERE id = :id") suspend fun forceState(id: String, s: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE nfts SET tokenId = :tokenId, mintTxHash = :txHash, mintBlockNumber = :block, mintGasUsed = :gas, mintFeeEth = :fee, mintTimestamp = :ts, state = 'MINTED', updatedAt = :ts WHERE id = :id") suspend fun setMinted(id: String, tokenId: String, txHash: String, block: Long, gas: Long, fee: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE nfts SET listingId = :listingId, state = 'LISTED', updatedAt = :ts WHERE id = :id") suspend fun setListed(id: String, listingId: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE nfts SET saleId = :saleId, state = 'SOLD', updatedAt = :ts WHERE id = :id") suspend fun setSold(id: String, saleId: String, ts: Long = System.currentTimeMillis())
    @Query("SELECT COUNT(*) FROM nfts WHERE collectionId = :cid AND state = :s") suspend fun countByState(cid: String, s: String): Int
}

@Dao interface MintJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(job: MintJobEntity)
    @Query("SELECT * FROM mint_jobs WHERE id = :id") suspend fun getById(id: String): MintJobEntity?
    @Query("SELECT * FROM mint_jobs WHERE nftId = :nftId ORDER BY scheduledAt DESC LIMIT 1") suspend fun getLatestForNft(nftId: String): MintJobEntity?
    @Query("SELECT * FROM mint_jobs WHERE state IN ('PENDING', 'RUNNING', 'TRANSACTION_SUBMITTED', 'CONFIRMATION_PENDING', 'UNKNOWN') ORDER BY scheduledAt ASC") suspend fun getPendingJobs(): List<MintJobEntity>
    @Query("UPDATE mint_jobs SET state = :s, txHash = :txHash, nonce = :nonce, updatedAt = :ts WHERE id = :id") suspend fun setSubmitted(id: String, s: String = "TRANSACTION_SUBMITTED", txHash: String, nonce: Long, ts: Long = System.currentTimeMillis())
    @Query("UPDATE mint_jobs SET state = :s, gasUsed = :gas, completedAt = :ts, updatedAt = :ts WHERE id = :id") suspend fun setComplete(id: String, s: String, gas: Long?, ts: Long = System.currentTimeMillis())
    @Query("UPDATE mint_jobs SET state = :s, errorMessage = :err, retryCount = retryCount + 1, updatedAt = :ts WHERE id = :id") suspend fun setError(id: String, s: String, err: String, ts: Long = System.currentTimeMillis())
}

@Dao interface ListingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(l: ListingEntity)
    @Query("SELECT * FROM listings WHERE id = :id") suspend fun getById(id: String): ListingEntity?
    @Query("SELECT * FROM listings WHERE nftId = :nftId ORDER BY createdAt DESC LIMIT 1") suspend fun getLatestForNft(nftId: String): ListingEntity?
    @Query("SELECT * FROM listings WHERE state = 'LISTED'") suspend fun getActiveListing(): List<ListingEntity>
    @Query("UPDATE listings SET state = :s, orderHash = :hash, listingUrl = :url, updatedAt = :ts WHERE id = :id") suspend fun setListed(id: String, s: String = "LISTED", hash: String?, url: String?, ts: Long = System.currentTimeMillis())
    @Query("UPDATE listings SET state = :s, errorMessage = :err, updatedAt = :ts WHERE id = :id") suspend fun setError(id: String, s: String, err: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE listings SET state = :s, updatedAt = :ts WHERE id = :id") suspend fun setState(id: String, s: String, ts: Long = System.currentTimeMillis())
}

@Dao interface SettingsDao {
    @Query("SELECT value FROM system_settings WHERE key = :key") suspend fun get(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(setting: SystemSettingEntity)
    @Query("DELETE FROM system_settings WHERE key = :key") suspend fun delete(key: String)
}

@Dao interface NftSummaryDao {
    @Query("SELECT COUNT(*) FROM nfts WHERE state = :s") suspend fun countGlobalByState(s: String): Int
    @Query("SELECT COUNT(*) FROM nfts") suspend fun countAll(): Int
}
