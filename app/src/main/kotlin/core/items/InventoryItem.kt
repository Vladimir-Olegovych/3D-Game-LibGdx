package core.items

import com.badlogic.gdx.graphics.g2d.TextureRegion

data class InventoryItem(
    val item: Item,
    val texture: TextureRegion,
    var count: Int
) {
    fun refreshCount() {

    }
}