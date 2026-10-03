package com.sadhu.nftautopilot.data.database.dao
import androidx.room.*
import com.sadhu.nftautopilot.data.database.entity.*

@Dao interface TraitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLayer(layer: TraitLayerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertValue(value: TraitValueEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRule(rule: TraitRuleEntity)
    @Query("SELECT * FROM trait_layers WHERE collectionId = :cid") suspend fun getLayersForCollection(cid: String): List<TraitLayerEntity>
    @Query("SELECT * FROM trait_rules WHERE collectionId = :cid") suspend fun getRulesForCollection(cid: String): List<TraitRuleEntity>
    @Query("SELECT * FROM trait_values WHERE layerId = :layerId") suspend fun getValuesForLayer(layerId: String): List<TraitValueEntity>
}
