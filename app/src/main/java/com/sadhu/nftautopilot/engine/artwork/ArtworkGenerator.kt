package com.sadhu.nftautopilot.engine.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.util.Log
import com.sadhu.nftautopilot.data.database.dao.ArtworkDao
import com.sadhu.nftautopilot.data.database.dao.TraitDao
import com.sadhu.nftautopilot.data.database.entity.ArtworkEntity
import com.sadhu.nftautopilot.data.database.entity.TraitLayerEntity
import com.sadhu.nftautopilot.data.database.entity.TraitValueEntity
import com.sadhu.nftautopilot.util.HashUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class SelectedTrait(
    val layer: TraitLayerEntity,
    val value: TraitValueEntity
)

data class GeneratedArtwork(
    val artworkId: String,
    val file: File,
    val traits: List<SelectedTrait>,
    val rarityScore: Double,
    val isDuplicate: Boolean
)

@Singleton
class ArtworkGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val traitDao: TraitDao,
    private val artworkDao: ArtworkDao,
    private val rarityEngine: RarityEngine,
    private val duplicateDetector: DuplicateDetector
) {
    private val TAG = "ArtworkGenerator"
    val outputDir: File get() = File(context.filesDir, "artworks").also { it.mkdirs() }

    /**
     * Generate one NFT artwork for the given collection.
     * Returns null if trait selection fails due to rule conflicts.
     */
    suspend fun generateOne(collectionId: String, tokenIndex: Int): GeneratedArtwork? {
        val layers = traitDao.getLayersForCollection(collectionId)
        if (layers.isEmpty()) error("No trait layers configured for collection $collectionId")

        // Select traits using weighted random + rule engine
        val selected = selectTraits(collectionId, layers) ?: return null

        // Composite layers into bitmap
        val bitmap = compositeLayers(collectionId, selected)
        val artworkId = UUID.randomUUID().toString()
        val outputFile = File(outputDir, "${collectionId}_$tokenIndex.png")

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        // Hashing for duplicate detection
        val sha256 = HashUtil.sha256File(outputFile)
        val pHash = duplicateDetector.computePHash(outputFile)

        // Check for duplicates
        val isDuplicate = duplicateDetector.isDuplicate(collectionId, sha256, pHash)
        if (isDuplicate) {
            outputFile.delete()
            Log.w(TAG, "Duplicate detected for tokenIndex=$tokenIndex")
        }

        val traitIdsCsv = selected.joinToString(",") { it.value.id }
        val rarityScore = rarityEngine.calculateRarityScore(selected, layers.size)

        val entity = ArtworkEntity(
            id = artworkId,
            collectionId = collectionId,
            tokenIndex = tokenIndex,
            localFilePath = if (isDuplicate) "" else outputFile.absolutePath,
            sha256Hash = sha256,
            pHashValue = pHash,
            rarityScore = rarityScore,
            selectedTraitValueIds = traitIdsCsv,
            status = if (isDuplicate) "DUPLICATE_REJECTED" else "GENERATED"
        )
        artworkDao.insert(entity)

        return GeneratedArtwork(artworkId, outputFile, selected, rarityScore, isDuplicate)
    }

    // ─── Trait selection with weighted random + compatibility rules ────────────

    private suspend fun selectTraits(collectionId: String, layers: List<TraitLayerEntity>): List<SelectedTrait>? {
        val rules = traitDao.getRulesForCollection(collectionId)
        val selected = mutableListOf<SelectedTrait>()

        for (layer in layers.sortedBy { it.displayOrder }) {
            val values = traitDao.getValuesForLayer(layer.id)
            if (values.isEmpty()) {
                if (layer.isRequired) return null
                continue
            }

            // Filter by compatibility rules
            val allowedValues = values.filter { candidate ->
                isCompatible(candidate, selected, rules)
            }
            if (allowedValues.isEmpty()) return null

            val pick = weightedRandom(allowedValues) ?: return null
            selected += SelectedTrait(layer, pick)
        }
        return selected
    }

    private fun isCompatible(
        candidate: TraitValueEntity,
        alreadySelected: List<SelectedTrait>,
        rules: List<com.sadhu.nftautopilot.data.database.entity.TraitRuleEntity>
    ): Boolean {
        for (selectedTrait in alreadySelected) {
            val triggerRules = rules.filter { it.triggerTraitValueId == selectedTrait.value.id }
            for (rule in triggerRules) {
                if (rule.targetTraitValueId == candidate.id) {
                    if (rule.ruleType == "PROHIBIT") return false
                }
            }
        }
        return true
    }

    private fun weightedRandom(values: List<TraitValueEntity>): TraitValueEntity? {
        if (values.isEmpty()) return null
        val totalWeight = values.sumOf { it.rarityWeight }
        if (totalWeight <= 0) return values.random()
        var roll = Random.nextInt(totalWeight)
        for (value in values) {
            roll -= value.rarityWeight
            if (roll < 0) return value
        }
        return values.last()
    }

    // ─── Layer compositing with Android Canvas ────────────────────────────────

    private fun compositeLayers(collectionId: String, traits: List<SelectedTrait>): Bitmap {
        val size = 1000
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for (trait in traits.sortedBy { it.layer.displayOrder }) {
            val assetPath = "layers/$collectionId/${trait.layer.name}/${trait.value.assetFileName}"
            try {
                context.assets.open(assetPath).use { stream ->
                    val layerBitmap = BitmapFactory.decodeStream(stream)
                    if (layerBitmap != null) {
                        val scaled = Bitmap.createScaledBitmap(layerBitmap, size, size, true)
                        canvas.drawBitmap(scaled, 0f, 0f, null)
                        if (scaled != layerBitmap) scaled.recycle()
                        layerBitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load layer asset: $assetPath — ${e.message}")
            }
        }
        return result
    }
}
