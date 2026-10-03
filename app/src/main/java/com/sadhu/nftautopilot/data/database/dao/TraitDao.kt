package com.sadhu.nftautopilot.data.database.dao
import androidx.room.*
import com.sadhu.nftautopilot.data.database.entity.*

@Dao interface TraitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLayer(layer: TraitLayerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertValue(value: TraitValueEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRule(rule: TraitRuleEntity)
    @Query("SELECT * FROM trait_layers WHERE collectionId = :cid") suspend fun getLayers(cid: String): List<TraitLayerEntity>
    @Query("SELECT * FROM trait_values WHERE collectionId = :cid") suspend fun getValues(cid: String): List<TraitValueEntity>
    @Query("SELECT * FROM trait_rules WHERE collectionId = :cid") suspend fun getRules(cid: String): List<TraitRuleEntity>
}
