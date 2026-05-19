package core.configs

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.storage.json.AppConfig
import core.assets.AssetsSetupManager
import core.blocks.BlockDataSO

object ConfigSetupManager: LaunchedEffect {
    override fun launch(context: Context) {
        context.setObject(
            customKey = ConfigTypes.BLOCK_DATA_SO,
            AppConfig(
                configName = "${AssetsSetupManager.ASSETS_PATH}/configs/${ConfigTypes.BLOCK_DATA_SO}",
                default = BlockDataSO(
                    textureSizeX = 0.1f,
                    textureSizeY = 0.1f,
                    emptyList()
                )
            )
        )
        //---
    }
}