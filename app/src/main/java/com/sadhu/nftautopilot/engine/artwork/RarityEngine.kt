package com.sadhu.nftautopilot.engine.artwork

import com.sadhu.nftautopilot.data.database.dao.ArtworkDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RarityEngine @Inject constructor(
    private val artworkDao: ArtworkDao
) {
    /** Rarity score = sum of (1 / trait frequency) across all traits */
    fun calculateRarityScore(traits: List<SelectedTrait>, totalLayers: Int): Double {
        if (traits.isEmpty()) return 0.0
        return traits.sumOf { selected ->
            val weight = selected.value.rarityWeight.toDouble()
            val maxWeight = 10_000.0
            if (weight <= 0) 1.0 else (maxWeight / weight)
        }
    }

    fun getRarityCategory(score: Double, maxScore: Double): String = when {
        score >= maxScore * 0.95 -> "LEGENDARY"
        score >= maxScore * 0.80 -> "EPIC"
        score >= maxScore * 0.50 -> "RARE"
        else -> "COMMON"
    }

    /** Assign final ranks after all NFTs in a collection are generated. */
    suspend fun assignRanks(collectionId: String) {
        val artworks = artworkDao.getByCollection(collectionId)
            .filter { it.status == "GENERATED" }
            .sortedByDescending { it.rarityScore }

        val maxScore = artworks.firstOrNull()?.rarityScore ?: 1.0
        artworks.forEachIndexed { index, artwork ->
            val rank = index + 1
            val category = getRarityCategory(artwork.rarityScore, maxScore)
            artworkDao.updateRarity(artwork.id, artwork.rarityScore, rank, category)
        }
    }
}
