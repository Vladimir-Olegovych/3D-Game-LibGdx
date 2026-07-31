package core.items

import com.fasterxml.jackson.annotation.JsonProperty
import core.assets.SkinID

data class Item(
    @get:JsonProperty("id") @param:JsonProperty("id")
    val id: String = "",
    @get:JsonProperty("name") @param:JsonProperty("name")
    val name: String = "",
    @get:JsonProperty("description") @param:JsonProperty("description")
    val description: String = "",
    @get:JsonProperty("skinID") @param:JsonProperty("skinID")
    val skinID: SkinID = SkinID.BLOCK,
    @get:JsonProperty("regionName") @param:JsonProperty("regionName")
    val regionName: String = "",
    @get:JsonProperty("maxStack") @param:JsonProperty("maxStack")
    val maxStack: Int = 4,
    @get:JsonProperty("stackable") @param:JsonProperty("stackable")
    val stackable: Boolean = true
)