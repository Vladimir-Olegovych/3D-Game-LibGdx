package app.feature.game.event

import core.blocks.BlockType
import core.items.InventoryItem

sealed class InventoryEvent {
    class OnUpdate(val inventoryItem: InventoryItem?, val slot: Int)
    class OnSelectInventorySlot(val slot: Int)
    class OnAddBlockItem(val blockType: BlockType, val count: Int = 1)
}