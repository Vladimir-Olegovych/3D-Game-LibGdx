package core.blocks

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.storage.json.AppConfig
import core.assets.SkinID
import core.configs.ConfigTypes
import core.mesh.DirectionType

class BlockDataManager: LaunchedEffect
{
    private val blockTextureDataMap = HashMap<BlockType, TextureData>()
    private val blockFaceUvsMap = HashMap<BlockType, BlockFaceUvs>()

    fun getBlockTextureDataMap(): Map<BlockType, TextureData> = blockTextureDataMap

    override fun launch(gContext: GContext) {
        val assetManager = gContext.getObject<AssetManager>()
        val config = gContext.getObject<AppConfig<BlockDataSO>>(ConfigTypes.BLOCK_DATA_SO)
        val textureData = config.getConfig()
        val atlas = assetManager.get<TextureAtlas>(SkinID.BLOCK.atlas)

        blockTextureDataMap.clear()
        blockFaceUvsMap.clear()

        textureData.textureDataList.forEach { item ->
            if (!blockTextureDataMap.containsKey(item.blockType))
            {
                blockTextureDataMap[item.blockType] = item
                blockFaceUvsMap[item.blockType] = BlockFaceUvs(
                    up = createFaceUvs(findRegionOrError(atlas, item.regionNameUp, item.blockType, "up")),
                    side = createFaceUvs(findRegionOrError(atlas, item.regionNameSide, item.blockType, "side")),
                    down = createFaceUvs(findRegionOrError(atlas, item.regionNameDown, item.blockType, "down"))
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

    private fun createFaceUvs(region: TextureRegion): Array<Vector2> {
        val textureWidth = region.texture.width.toFloat()
        val textureHeight = region.texture.height.toFloat()

        val epsilonU = 1f / textureWidth
        val epsilonV = 1f / textureHeight

        val left = minOf(region.u, region.u2) + epsilonU
        val right = maxOf(region.u, region.u2) - epsilonU
        val top = minOf(region.v, region.v2) + epsilonV
        val bottom = maxOf(region.v, region.v2) - epsilonV

        return arrayOf(
            Vector2(left, top),
            Vector2(left, bottom),
            Vector2(right, bottom),
            Vector2(right, top)
        )
    }
}
