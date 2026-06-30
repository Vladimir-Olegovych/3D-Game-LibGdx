package app.feature.game.event

import core.items.InventoryItem

sealed class InventoryEvent {
    class OnUpdate(val inventoryItem: InventoryItem, val slot: Int)
}