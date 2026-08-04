package app.feature.game.ecs.components

import com.artemis.Component
import core.items.Item

class HoldingItemComponent: Component() {
    var item: Item? = null
    var dirty = false

    fun setHoldingItem(item: Item?) {
        this.item = item
        dirty = true
    }
}