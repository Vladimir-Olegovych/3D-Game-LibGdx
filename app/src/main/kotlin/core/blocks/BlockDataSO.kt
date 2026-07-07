package core.blocks

import com.fasterxml.jackson.annotation.JsonProperty
import com.gigapi.math.vector.IntVector2

class BlockDataSO(
    @get:JsonProperty("textureDataList") @param:JsonProperty("textureDataList")
    val textureDataList: List<TextureData>
)

class TextureData(
    @get:JsonProperty("blockType") @param:JsonProperty("blockType")
    val blockType: BlockType,
    @get:JsonProperty("regionNameUp") @param:JsonProperty("regionNameUp")
    val regionNameUp: String,
    @get:JsonProperty("regionNameSide") @param:JsonProperty("regionNameSide")
    val regionNameSide: String,
    @get:JsonProperty("regionNameDown") @param:JsonProperty("regionNameDown")
    val regionNameDown: String,
    @get:JsonProperty("solid") @param:JsonProperty("solid")
    val isSolid: Boolean,
    @get:JsonProperty("generateAllSides") @param:JsonProperty("generateAllSides")
    val generateAllSides: Boolean,
    @get:JsonProperty("generateCollider") @param:JsonProperty("generateCollider")
    val generateCollider: Boolean
)