package core.blocks

import com.fasterxml.jackson.annotation.JsonProperty
import core.blocks.types.BlockShape
import core.blocks.types.BlockType
import core.items.ToolType

class BlockDataSO(
    @get:JsonProperty("textureDataList") @param:JsonProperty("textureDataList")
    val textureDataList: List<TextureData> = emptyList(),
    @get:JsonProperty("blockInfoList") @param:JsonProperty("blockInfoList")
    val blockInfoDataList: List<BlockInfoData> = emptyList()
)
class BlockInfoData(
    @get:JsonProperty("blockType") @param:JsonProperty("blockType")
    val blockType: BlockType = BlockType.NOTHING,
    @get:JsonProperty("digTime") @param:JsonProperty("digTime")
    val digTime: Float = 1.0f,
    @get:JsonProperty("digItem") @param:JsonProperty("digItem")
    val digTool: ToolType = ToolType.SWORD,
)

class TextureData(
    @get:JsonProperty("blockType") @param:JsonProperty("blockType")
    val blockType: BlockType = BlockType.NOTHING,
    @get:JsonProperty("regionNameUp") @param:JsonProperty("regionNameUp")
    val regionNameUp: String? = null,
    @get:JsonProperty("regionNameSide") @param:JsonProperty("regionNameSide")
    val regionNameSide: String = "",
    @get:JsonProperty("regionNameDown") @param:JsonProperty("regionNameDown")
    val regionNameDown: String? = null,
    @get:JsonProperty("pickable") @param:JsonProperty("pickable")
    val pickable: Boolean = true,
    @get:JsonProperty("generateAllSides") @param:JsonProperty("generateAllSides")
    val generateAllSides: Boolean = false,
    @get:JsonProperty("generateCollider") @param:JsonProperty("generateCollider")
    val generateCollider: Boolean = true,
    @get:JsonProperty("shape") @param:JsonProperty("shape")
    val shape: BlockShape = BlockShape.CUBE
)