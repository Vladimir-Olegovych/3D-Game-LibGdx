package core.blocks

import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.storage.json.AppConfig
import core.configs.ConfigTypes

class BlockDataManager: LaunchedEffect
{
    private val textureOffset = 0.001f
    private var tileSizeX = 0f
    private var tileSizeY = 0f
    private val blockTextureDataMap = HashMap<BlockType, TextureData>()

    fun getBlockTextureDataMap(): Map<BlockType, TextureData> = blockTextureDataMap
    fun getTextureOffset() = textureOffset
    fun getTileSizeX() = tileSizeX
    fun getTileSizeY() = tileSizeY

    override fun launch(context: Context) {
        val config = context.getObject<AppConfig<BlockDataSO>>(ConfigTypes.BLOCK_DATA_SO)
        val textureData = config.getConfig()
        textureData.textureDataList.forEach { item ->
            if (!blockTextureDataMap.containsKey(item.blockType))
            {
                blockTextureDataMap[item.blockType] = item;
            };
        }
        tileSizeX = textureData.textureSizeX;
        tileSizeY = textureData.textureSizeY;
    }
}