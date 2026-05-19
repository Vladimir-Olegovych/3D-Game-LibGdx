package core.blocks

import com.fasterxml.jackson.annotation.JsonProperty
import com.gigapi.math.vector.IntVector2

class BlockDataSO(
    @get:JsonProperty("textureSizeX") @param:JsonProperty("textureSizeX")
    val textureSizeX: Float,
    @get:JsonProperty("textureSizeY") @param:JsonProperty("textureSizeY")
    val textureSizeY: Float,
    @get:JsonProperty("textureDataList") @param:JsonProperty("textureDataList")
    val textureDataList: List<TextureData>
)

class TextureData(
    @get:JsonProperty("blockType") @param:JsonProperty("blockType")
    val blockType: BlockType,
    @get:JsonProperty("up") @param:JsonProperty("up")
    val up: IntVector2,
    @get:JsonProperty("down") @param:JsonProperty("down")
    val down: IntVector2,
    @get:JsonProperty("side") @param:JsonProperty("side")
    val side: IntVector2,
    @get:JsonProperty("solid") @param:JsonProperty("solid")
    val isSolid: Boolean = true,
    @get:JsonProperty("generatesCollider") @param:JsonProperty("generatesCollider")
    val generatesCollider: Boolean = true
)