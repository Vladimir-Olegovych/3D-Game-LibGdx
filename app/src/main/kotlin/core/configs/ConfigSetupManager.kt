package core.configs

import com.badlogic.gdx.Gdx
import com.fasterxml.jackson.databind.ObjectMapper
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.storage.json.AppConfig
import core.blocks.BlockDataSO
import core.crafting.CraftingRecipeSO
import core.items.ItemDataSO

object ConfigSetupManager: LaunchedEffect {
    override fun launch(gContext: GContext) {
        val om = gContext.getObject<ObjectMapper>()
        val default = om.readValue(
            Gdx.files.internal("configs/${ConfigTypes.BLOCK_DATA_SO}.json").read(),
            BlockDataSO::class.java
        )
        gContext.setObject(
            customKey = ConfigTypes.BLOCK_DATA_SO,
            AppConfig(
                configName = "configs/${ConfigTypes.BLOCK_DATA_SO}",
                default = default
            )
        )
        //---
        val itemsDefault = om.readValue(
            Gdx.files.internal("configs/${ConfigTypes.ITEM_DATA_SO}.json").read(),
            ItemDataSO::class.java
        )
        gContext.setObject(
            customKey = ConfigTypes.ITEM_DATA_SO,
            AppConfig(
                configName = "configs/${ConfigTypes.ITEM_DATA_SO}",
                default = itemsDefault
            )
        )
        // ---
        val craftingDefault = om.readValue(
            Gdx.files.internal("configs/${ConfigTypes.CRAFTING_RECIPES_SO}.json").read(),
            CraftingRecipeSO::class.java
        )
        gContext.setObject(
            customKey = ConfigTypes.CRAFTING_RECIPES_SO,
            AppConfig(
                configName = "configs/${ConfigTypes.CRAFTING_RECIPES_SO}",
                default = craftingDefault
            )
        )
        //---
    }
}
