package core.configs

import com.badlogic.gdx.Gdx
import com.fasterxml.jackson.databind.ObjectMapper
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.storage.json.AppConfig
import core.blocks.BlockDataSO

object ConfigSetupManager: LaunchedEffect {
    override fun launch(context: GContext) {
        val om = context.getObject<ObjectMapper>()
        val default = om.readValue(
            Gdx.files.internal("configs/${ConfigTypes.BLOCK_DATA_SO}.json").read(),
            BlockDataSO::class.java
        )
        context.setObject(
            customKey = ConfigTypes.BLOCK_DATA_SO,
            AppConfig(
                configName = "configs/${ConfigTypes.BLOCK_DATA_SO}",
                default = default
            )
        )
        //---
    }
}
