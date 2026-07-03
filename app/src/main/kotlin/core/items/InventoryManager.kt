package core.items

import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import app.feature.game.event.InventoryEvent
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import core.assets.SkinID
import core.blocks.BlockType

class InventoryManager(
    val inventorySize: Int = 32,
    val cols: Int = 8,
    val rows: Int = inventorySize / cols
): LaunchedEffect, DisposableEffect {

    private val inventorySlots = Array<InventoryItem?>(inventorySize) { null }

    private lateinit var assetManager: AssetManager
    private lateinit var eventBus: EventBus

    override fun launch(context: Context) {
        assetManager = context.getObject()
        eventBus = context.getObject(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)

        for (i in 0 until 8) {
            addItem(testItem)
        }
    }

    @BusEvent
    fun onBlockRemoved(event: GameEvent.OnBlockRemoved) {
        val blockType = event.blockType
        if (blockType == BlockType.AIR) return
        val item = Item(
            name = blockType.name,
            description = "Block",
            skinID = SkinID.BLOCK,
            regionName = blockType.regionName,
            maxStack = 8,
            stackable = true
        )
        addItem(item)
    }

    fun swapIndexes(from: Int, to: Int) {
        val itemFrom = inventorySlots[from]
        inventorySlots[from] = inventorySlots[to]
        inventorySlots[to] = itemFrom
        eventBus.sendEvent(InventoryEvent.OnUpdate(inventorySlots[from], from))
        eventBus.sendEvent(InventoryEvent.OnUpdate(itemFrom, to))
    }

    fun getInventoryItem(index: Int): InventoryItem? {
        return inventorySlots[index]
    }

    fun addItem(item: Item): Boolean {
        for (i in 0 until inventorySize) {
            val itemInSlot = inventorySlots[i]?: continue
            if( itemInSlot.item == item &&
                itemInSlot.count < itemInSlot.item.maxStack &&
                itemInSlot.item.stackable
              ) {
                itemInSlot.count++
                itemInSlot.refreshCount()
                eventBus.sendEvent(InventoryEvent.OnUpdate(itemInSlot, i))
                return true
            }
        }
        for (i in 0 until inventorySize) {
            val itemInSlot = inventorySlots[i]
            if(itemInSlot == null)
            {
                setSlotItem(item, i)
                return true
            }
        }
        return false
    }

    private fun setSlotItem(item: Item, slot: Int) {
        val textureRegion = assetManager.get<TextureAtlas>(item.skinID.atlas).findRegion(item.regionName)
        val inventoryItem = InventoryItem(
            item = item,
            texture = textureRegion,
            count = 1
        )
        inventorySlots[slot] = inventoryItem
        eventBus.sendEvent(InventoryEvent.OnUpdate(inventoryItem, slot))
    }

    override fun dispose() {
        for (i in 0 until inventorySize) {
            inventorySlots[i] = null
        }
    }

}