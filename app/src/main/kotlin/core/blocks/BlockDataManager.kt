package core.blocks

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.storage.json.AppConfig
import core.assets.SkinID
import core.blocks.types.BlockShape
import core.blocks.types.BlockType
import core.configs.ConfigTypes
import core.mesh.DirectionType
import core.mesh.MeshUtils.createFaceUvs

class BlockDataManager: LaunchedEffect
{
    private val blockTextureDataMap = HashMap<BlockType, TextureData>()
    private val blockInfoDataMap = HashMap<BlockType, BlockInfoData>()
    private val blockFaceUvsMap = HashMap<BlockType, BlockFaceUvs>()
    private val shapeByOrdinal = Array(BlockType.entries.size) { BlockShape.CUBE }
    private val colliderByOrdinal = BooleanArray(BlockType.entries.size)
    private val pickOnlyByOrdinal = BooleanArray(BlockType.entries.size)

    fun shapeOf(t: BlockType) = shapeByOrdinal[t.ordinal]
    fun hasCollider(t: BlockType) = colliderByOrdinal[t.ordinal]
    fun isPickOnly(t: BlockType) = pickOnlyByOrdinal[t.ordinal]

    fun getBlockTextureDataMap(): Map<BlockType, TextureData> = blockTextureDataMap
    fun getBlockInfoDataMap(): Map<BlockType, BlockInfoData> = blockInfoDataMap

    override fun launch(gContext: GContext) {
        val assetManager = gContext.getObject<AssetManager>()
        val config = gContext.getObject<AppConfig<BlockDataSO>>(ConfigTypes.BLOCK_DATA_SO)
        val textureData = config.getConfig()
        val atlas = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas)

        blockInfoDataMap.clear()
        blockTextureDataMap.clear()
        blockFaceUvsMap.clear()

        textureData.blockInfoDataList.forEach { item ->
            blockInfoDataMap[item.blockType] = item
        }

        textureData.textureDataList.forEach { item ->
            if (!blockTextureDataMap.containsKey(item.blockType)) {
                blockTextureDataMap[item.blockType] = item
                shapeByOrdinal[item.blockType.ordinal] = item.shape
                colliderByOrdinal[item.blockType.ordinal] = item.generateCollider
                pickOnlyByOrdinal[item.blockType.ordinal] = item.pickable && !item.generateCollider

                val side = createFaceUvs(findRegionOrError(atlas, item.regionNameSide, item.blockType, "side"))
                blockFaceUvsMap[item.blockType] = BlockFaceUvs(
                    up = item.regionNameUp?.let { createFaceUvs(findRegionOrError(atlas, it, item.blockType, "up")) } ?: side,
                    side = side,
                    down = item.regionNameDown?.let { createFaceUvs(findRegionOrError(atlas, it, item.blockType, "down")) } ?: side
                )
            }
        }
    }

    fun faceUVs(directionType: DirectionType, blockType: BlockType): Array<Vector2> {
        val faceUvs = blockFaceUvsMap[blockType] ?: error("No UV data for block type: $blockType")
        return when (directionType) {
            DirectionType.UP -> faceUvs.up
            DirectionType.DOWN -> faceUvs.down
            else -> faceUvs.side
        }
    }

    private fun findRegionOrError(
        atlas: TextureAtlas,
        regionName: String,
        blockType: BlockType,
        faceType: String
    ): TextureRegion {
        return atlas.findRegion(regionName)
            ?: error("Region '$regionName' ($faceType) not found for block type: $blockType")
    }

}
