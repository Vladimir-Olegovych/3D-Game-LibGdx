package app.feature.game.ecs.systems

import app.feature.game.ecs.components.DiggingComponent
import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import core.blocks.BlockDataManager
import core.bullet.raycast.RayCastTypes
import core.controls.PlayerInputProcessor
import core.defaults.WorldConstants
import core.items.InventoryManager

@All(DiggingComponent::class)
class BlockProcessingSystem: IteratingSystem() {

    @Wire(name = EventBusTypes.MAIN_EVENT_BUS)
    private lateinit var mainEventBus: EventBus
    @Wire
    private lateinit var playerInputProcessor: PlayerInputProcessor
    @Wire
    private lateinit var inventoryManager: InventoryManager
    @Wire
    private lateinit var blockDataManager: BlockDataManager


    private var playerDiggingComponentId: Int? = null
    private var playerItemSlot: Int? = null

    private lateinit var diggingComponent: ComponentMapper<DiggingComponent>

    @BusEvent
    fun onBlockRayCastResult(event: GameEvent.OnRayCastBlockResult) {
        if (event.requestId != RayCastTypes.CHUNK_DIG_RAY_CAST) return
        val blockInfoDataMap = blockDataManager.getBlockInfoDataMap()
        val itemSlot = playerInputProcessor.getSelectedSlot()

        val entityId = event.chunkEntityId
        val component = diggingComponent[entityId]?: diggingComponent.create(entityId)
        if (component.chunkPosition != event.chunkPosition ||
            component.blockPosition != event.blockPosition ||
            playerItemSlot != itemSlot)
        {
            val blockToRemoveInfo = blockInfoDataMap[event.blockToRemove]?: return
            val inventoryItem = inventoryManager.getInventoryItem(itemSlot)

            component.chunkPosition = event.chunkPosition
            component.blockPosition = event.blockPosition
            component.blockToSet = event.blockToSet
            component.blockToRemove = event.blockToRemove

            var digTime = blockToRemoveInfo.digTime
            if (blockToRemoveInfo.digTool == inventoryItem?.item?.toolType) {
                digTime /= 2
                digTime /= inventoryItem.item.toolLevel
            }
            if (digTime <= 0) { digTime = 0.001f }

            component.digTime = digTime
            component.process = 0f

            component.miningToolType = inventoryItem?.item?.toolType
            component.miningToolLevel = inventoryItem?.item?.toolLevel
        }
        if (playerDiggingComponentId != null && playerDiggingComponentId != entityId) {
            diggingComponent.remove(playerDiggingComponentId!!)
        }
        playerItemSlot = itemSlot
        playerDiggingComponentId = entityId
    }

    override fun begin() {
        val pdId = playerDiggingComponentId ?: return
        if (playerInputProcessor.isMouseLeft()) return

        diggingComponent.remove(pdId)
        playerDiggingComponentId = null
    }

    override fun process(entityId: Int) {
        val owner = if (playerDiggingComponentId == entityId) {
            WorldConstants.getLocalPlayerEntityId()
        } else {
            null
        }

        val component = diggingComponent[entityId] ?: return
        component.process += world.delta


        if (component.process <= component.digTime) return

        mainEventBus.sendEvent(ChunkEvent.OnSetBlock(
            owner = owner,
            chunkPosition = component.chunkPosition,
            blockPosition = component.blockPosition,
            blockType = component.blockToSet,
        ))
        diggingComponent.remove(entityId)
    }

}