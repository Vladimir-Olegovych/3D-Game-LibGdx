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
import com.gigapi.general.GContext
import com.gigapi.storage.json.AppConfig
import core.assets.SkinID
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.configs.ConfigTypes
import core.crafting.CraftingRecipeData

class InventoryManager(
    val inventorySize: Int = 32,
    val cols: Int = 8,
    val rows: Int = inventorySize / cols
): LaunchedEffect, DisposableEffect {

    private val inventorySlots = Array<InventoryItem?>(inventorySize) { null }

    private val itemMap = HashMap<String, Item>()

    private lateinit var blockDataManager: BlockDataManager
    private lateinit var assetManager: AssetManager
    private lateinit var eventBus: EventBus

    override fun launch(gContext: GContext) {
        blockDataManager = gContext.getObject()
        assetManager = gContext.getObject()
        eventBus = gContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)

        val config = gContext.getObject<AppConfig<ItemDataSO>>(ConfigTypes.ITEM_DATA_SO)
        val itemsData = config.getConfig()

        for (blockType in BlockType.entries) {
            val blockData = blockDataManager.getBlockTextureDataMap()[blockType]?: continue
            itemMap[blockType.name] = Item(
                id = blockType.name,
                name = blockType.name.lowercase(),
                description = "Block",
                skinID = SkinID.BLOCK,
                regionName = blockData.regionNameSide,
                maxStack = 64,
                stackable = true
            )
        }
        itemsData.items.forEach {
            item -> itemMap[item.id] = item
        }
    }

    @BusEvent
    fun onBlockRemoved(event: GameEvent.OnBlockRemoved) {
        val blockType = event.blockType
        if (blockType == BlockType.AIR) return
        val item = itemMap[blockType.name]?: return
        addItem(item)
    }

    fun craftItem(recipe: CraftingRecipeData): Boolean {
        if (!canCraft(recipe)) return false
        val resultItem = getItem(recipe.result.item) ?: return false
        val resultCount = recipe.result.count.coerceAtLeast(1)

        for (ingredient in recipe.ingredients) {
            if (!removeItem(ingredient.item, ingredient.count)) return false
        }

        if (!addItem(resultItem, resultCount)) {
            for (ingredient in recipe.ingredients) {
                val ingredientItem = getItem(ingredient.item) ?: continue
                addItem(ingredientItem, ingredient.count)
            }
            return false
        }
        return true
    }

    fun getItem(id: String): Item? {
        return itemMap[id]
    }

    fun hasSpaceFor(id: String, count: Int = 1): Boolean {
        if (count <= 0) return true
        val item = getItem(id) ?: return false
        var remaining = count

        if (item.stackable) {
            for (i in 0 until inventorySize) {
                if (remaining <= 0) return true
                val slot = inventorySlots[i] ?: continue
                if (slot.item.id != id) continue
                remaining -= (item.maxStack - slot.count).coerceAtLeast(0)
            }
        }

        if (remaining <= 0) return true

        val emptySlots = inventorySlots.count { it == null }
        if (!item.stackable) {
            return emptySlots >= remaining
        }

        val slotsNeeded = (remaining + item.maxStack - 1) / item.maxStack
        return emptySlots >= slotsNeeded
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

    fun canCraft(recipe: CraftingRecipeData): Boolean {
        if (recipe.ingredients.isEmpty()) return false
        return recipe.ingredients.all { countItem(it.item) >= it.count }
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
                eventBus.sendEvent(InventoryEvent.OnUpdate(null, i))
            } else {
                slot.refreshCount()
                eventBus.sendEvent(InventoryEvent.OnUpdate(slot, i))
            }
        }
        return remaining == 0
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
            eventBus.sendEvent(InventoryEvent.OnUpdate(itemInSlot, i))
        }

        while (remaining > 0) {
            val emptySlot = (0 until inventorySize).firstOrNull { inventorySlots[it] == null } ?: return false
            val stackCount = if (item.stackable) minOf(item.maxStack, remaining) else 1
            setSlotItem(item, emptySlot, stackCount)
            remaining -= stackCount
        }
        return true
    }

    private fun setSlotItem(item: Item, slot: Int, count: Int = 1) {
        val textureRegion = assetManager.get<TextureAtlas>(item.skinID.atlas).findRegion(item.regionName)
        val inventoryItem = InventoryItem(
            item = item,
            texture = textureRegion,
            count = count
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