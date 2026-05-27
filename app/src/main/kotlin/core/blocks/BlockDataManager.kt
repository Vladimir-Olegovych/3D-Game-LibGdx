package core.blocks

import com.badlogic.gdx.math.Vector2
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.storage.json.AppConfig
import core.configs.ConfigTypes
import core.mesh.DirectionType

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

    fun faceUVs(directionType: DirectionType, blockType: BlockType): Array<Vector2> {
        val texturePosition = texturePosition(directionType, blockType)
        val tileSizeX = getTileSizeX()
        val tileSizeY = getTileSizeY()
        val textureOffset = getTextureOffset()

        return arrayOf(
            Vector2(
                tileSizeX * texturePosition.x + textureOffset,
                tileSizeY * texturePosition.y + textureOffset
            ),
            Vector2(
                tileSizeX * texturePosition.x + textureOffset,
                tileSizeY * texturePosition.y + tileSizeY - textureOffset
            ),
            Vector2(
                tileSizeX * texturePosition.x + tileSizeX - textureOffset,
                tileSizeY * texturePosition.y + tileSizeY - textureOffset
            ),
            Vector2(
                tileSizeX * texturePosition.x + tileSizeX - textureOffset,
                tileSizeY * texturePosition.y + textureOffset
            )
        )
    }

    private fun texturePosition(directionType: DirectionType, blockType: BlockType): Vector2 {
        val textureDataMap = getBlockTextureDataMap()
        val textureData = textureDataMap[blockType] ?: error("No texture data for block type: $blockType")

        return when (directionType) {
            DirectionType.UP -> Vector2(textureData.up.x.toFloat(), textureData.up.y.toFloat())
            DirectionType.DOWN -> Vector2(textureData.down.x.toFloat(), textureData.down.y.toFloat())
            else -> Vector2(textureData.side.x.toFloat(), textureData.side.y.toFloat())
        }
    }
}