package core.blocks

import com.fasterxml.jackson.annotation.JsonProperty

class BlockDataSO(
    @get:JsonProperty("textureDataList") @param:JsonProperty("textureDataList")
    val textureDataList: List<TextureData> = emptyList()
)

class TextureData(
    @get:JsonProperty("blockType") @param:JsonProperty("blockType")
    val blockType: BlockType = BlockType.NOTHING,
    @get:JsonProperty("regionNameUp") @param:JsonProperty("regionNameUp")
    val regionNameUp: String = "",
    @get:JsonProperty("regionNameSide") @param:JsonProperty("regionNameSide")
    val regionNameSide: String = "",
    @get:JsonProperty("regionNameDown") @param:JsonProperty("regionNameDown")
    val regionNameDown: String = "",
    @get:JsonProperty("solid") @param:JsonProperty("solid")
    val isSolid: Boolean = true,
    @get:JsonProperty("generateAllSides") @param:JsonProperty("generateAllSides")
    val generateAllSides: Boolean = false,
    @get:JsonProperty("generateCollider") @param:JsonProperty("generateCollider")
    val generateCollider: Boolean = true
)