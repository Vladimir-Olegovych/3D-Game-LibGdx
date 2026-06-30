package core.items

import core.assets.SkinID

data class Item(
    val name: String,
    val description: String,
    val skinID: SkinID,
    val regionName: String,
    val maxStack: Int = 4,
    val stackable: Boolean = false
)

val testItem = Item(
    name = "Test Item",
    description = "Test description",
    skinID = SkinID.BLOCK,
    regionName = "brick_red",
)