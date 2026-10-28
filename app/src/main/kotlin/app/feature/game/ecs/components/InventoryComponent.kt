package app.feature.game.ecs.components

import com.artemis.Component
import core.items.InventoryItem
import core.items.Item

class InventoryComponent: Component() {

    interface InventoryListener {
        fun onSelectSlot(slot: Int) {}
        fun onUpdate(inventoryItem: InventoryItem?, slot: Int) {}
    }

    private val listeners = mutableSetOf<InventoryListener>()

    fun addListener(listener: InventoryListener) {
        listeners += listener
    }

    fun removeListener(listener: InventoryListener) {
        listeners -= listener
    }

    var inventorySize: Int = 32
        private set
    var cols: Int = 8
        private set
    var rows: Int = inventorySize / cols
        private set

    var inventorySlots = Array<InventoryItem?>(inventorySize) { null }
        private set

    var selectedSlot = 0
        private set

    fun setSelectedSlot(slot: Int) {
        if (selectedSlot == slot) return
        selectedSlot = slot
        listeners.forEach { it.onSelectSlot(slot) }
    }

    fun updateSizes(inventorySize: Int, cols: Int) {
        inventorySlots.forEach { if (it != null) return }

        this.inventorySize = inventorySize
        this.cols = cols
        rows = inventorySize / cols

        this.inventorySlots = Array(inventorySize) { null }
    }

    fun getSelectedInventoryItem(): InventoryItem? {
        return inventorySlots[selectedSlot]
    }

    fun swapIndexes(from: Int, to: Int) {
        val itemFrom = inventorySlots[from]
        inventorySlots[from] = inventorySlots[to]
        inventorySlots[to] = itemFrom
        listeners.forEach { it.onUpdate(inventorySlots[from], from) }
        listeners.forEach { it.onUpdate(itemFrom, to) }
    }

    fun getInventoryItem(index: Int): InventoryItem? {
        return inventorySlots[index]
    }

    fun setSlotItem(item: Item, slot: Int, count: Int = 1) {
        val inventoryItem = InventoryItem(
            item = item,
            count = count
        )
        inventorySlots[slot] = inventoryItem
        listeners.forEach { it.onUpdate(inventoryItem, slot) }
    }

    fun countItem(id: String): Int {
        var total = 0
        for (i in 0 until inventorySize) {
            val slot = inventorySlots[i] ?: continue
            if (slot.item.id == id) {
                total += slot.count
            }
        }
        return total
    }

    fun removeItem(id: String, count: Int, slot: Int): Boolean {
        if (count <= 0) return true
        if (countItem(id) < count) return false
        val inventoryItem = inventorySlots[slot] ?: return false
        if (inventoryItem.item.id != id) return false
        inventoryItem.count -= count

        if (inventoryItem.count <= 0) {
            inventorySlots[slot] = null
            listeners.forEach { it.onUpdate(null, slot) }
        } else {
            inventoryItem.refreshCount()
            listeners.forEach { it.onUpdate(inventoryItem, slot) }
        }
        return true
    }

    fun removeItem(id: String, count: Int): Boolean {
        if (count <= 0) return true
        if (countItem(id) < count) return false

        var remaining = count
        for (i in 0 until inventorySize) {
            if (remaining <= 0) break
            val slot = inventorySlots[i] ?: continue
            if (slot.item.id != id) continue

            val take = minOf(slot.count, remaining)
            slot.count -= take
            remaining -= take

            if (slot.count <= 0) {
                inventorySlots[i] = null
                listeners.forEach { it.onUpdate(null, i) }
            } else {
                slot.refreshCount()
                listeners.forEach { it.onUpdate(slot, i) }
            }
        }
        return remaining == 0
    }

    fun addItem(item: Item, count: Int = 1): Boolean {
        if (count <= 0) return true
        var remaining = count

        for (i in 0 until inventorySize) {
            if (remaining <= 0) break
            val itemInSlot = inventorySlots[i] ?: continue
            if (itemInSlot.item != item || !itemInSlot.item.stackable || itemInSlot.count >= itemInSlot.item.maxStack) {
                continue
            }
            val space = itemInSlot.item.maxStack - itemInSlot.count
            val add = minOf(space, remaining)
            itemInSlot.count += add
            remaining -= add
            itemInSlot.refreshCount()
            listeners.forEach { it.onUpdate(itemInSlot, i) }
        }

        while (remaining > 0) {
            val emptySlot = (0 until inventorySize).firstOrNull { inventorySlots[it] == null } ?: return false
            val stackCount = if (item.stackable) minOf(item.maxStack, remaining) else 1
            setSlotItem(item, emptySlot, stackCount)
            remaining -= stackCount
        }
        return true
    }

}