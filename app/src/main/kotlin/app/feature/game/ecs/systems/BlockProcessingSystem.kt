package app.feature.game.ecs.systems

import app.feature.game.ecs.components.DiggingComponent
import app.feature.game.ecs.components.HoldingItemComponent
import app.feature.game.ecs.components.InventoryComponent
import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import core.blocks.types.BlockType
import core.defaults.CastTypes
import core.items.ItemManager

@All(DiggingComponent::class)
class BlockProcessingSystem: IteratingSystem() {

    @Wire(name = EventBusTypes.CHUNK_EVENT_BUS)
    private lateinit var chunkEventBus: EventBus
    @Wire
    private lateinit var itemManager: ItemManager

    private lateinit var inventoryMapper: ComponentMapper<InventoryComponent>
    private lateinit var diggingMapper: ComponentMapper<DiggingComponent>
    private lateinit var holdingItemMapper: ComponentMapper<HoldingItemComponent>

    override fun process(entityId: Int) {
        val diggingComponent = diggingMapper[entityId]?: return
        diggingComponent.process += world.delta
        if (diggingComponent.process <= diggingComponent.digTime) return
        chunkEventBus.sendEvent(ChunkEvent.OnSetBlock(
            owner = entityId,
            isPlaceContext = false,
            chunkPosition = diggingComponent.chunkPosition,
            blockPosition = diggingComponent.blockPosition,
            blockType = diggingComponent.blockToSet,
        ))
        diggingMapper.remove(entityId)
    }

    @BusEvent
    fun onBlockRayCastResultPlace(event: ChunkEvent.OnRayCastResult) {
        if (event.requestId != CastTypes.Chunk.CHUNK_PLACE_RAY_CAST) return
        val inventoryComponent = inventoryMapper[event.requestOwner]?: return
        val itemToSet = inventoryComponent.getSelectedInventoryItem()?: return

        val blockToSet = BlockType.fromName(itemToSet.item.name)?: return
        chunkEventBus.sendEvent(ChunkEvent.OnSetBlock(
            owner = event.requestOwner,
            isPlaceContext = true,
            chunkPosition = event.chunkPosition,
            blockPosition = event.blockPosition,
            blockType = blockToSet,
        ))
    }

    @BusEvent
    fun onSetBlockFeedBack(event: ChunkEvent.OnSetBlockFeedBack) {
        if (!event.isSuccess) return
        val inventoryComponent = inventoryMapper[event.owner]?: return

        if (!event.isPlaceContext) {
            val item = itemManager.getItem(event.removedBlockType)
            item?.let { inventoryComponent.addItem(item) }
        } else {
            val slot = inventoryComponent.selectedSlot
            inventoryComponent.removeItem(event.setBlockType.name, 1, slot)
        }

        holdingItemMapper[event.owner]?.let {
            val selected = inventoryComponent.getSelectedInventoryItem()
            it.setHoldingItem(selected?.item)
        }
    }
}