package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
import app.feature.game.event.EventBusTypes
import app.feature.game.event.PhysicsEvent
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.IntMap
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import core.blocks.BlockDataManager
import core.blocks.types.BlockType
import core.bullet.raycast.RayCastTypes
import core.defaults.PlayerConstants

@All(LookDirectionComponent::class, InventoryComponent::class)
class BlockRayCastSystem : IteratingSystem() {

    @Wire(name = EventBusTypes.PHYSICS_EVENT_BUS)
    private lateinit var physicsEventBus: EventBus
    @Wire
    private lateinit var blockDataManager: BlockDataManager

    private lateinit var inventoryMapper: ComponentMapper<InventoryComponent>
    private lateinit var diggingMapper: ComponentMapper<DiggingComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>
    private lateinit var holdAttackMapper: ComponentMapper<HoldAttackComponent>
    private lateinit var transformMapper: ComponentMapper<TransformComponent>

    private var time = 0f
    private val lastRayCastTime = IntMap<Float>()
    private val lastHitTime = IntMap<Float>()

    override fun begin() {
        time += world.delta
    }

    override fun process(entityId: Int) {
        if (!holdAttackMapper.has(entityId)) {
            stopDigging(entityId)
            return
        }

        val lastHit = lastHitTime[entityId]
        if (lastHit != null && time - lastHit > HIT_TIMEOUT && diggingMapper.has(entityId)) {
            diggingMapper.remove(entityId)
        }

        val lastRayCast = lastRayCastTime[entityId]
        if (lastRayCast != null && time - lastRayCast < RAY_CAST_INTERVAL) {
            return
        }
        lastRayCastTime.put(entityId, time)

        val transform = transformMapper[entityId]?.transform ?: return
        val look = lookDirectionMapper[entityId] ?: return

        val from = Vector3().also { transform.getTranslation(it) }
            .add(PlayerConstants.cameraOffset)
        val direction = Vector3(look.direction).nor()

        physicsEventBus.sendEvent(
            PhysicsEvent.OnRayCastRequest(
                requestId = RayCastTypes.CHUNK_DIG_RAY_CAST,
                requestOwner = entityId,
                from = from,
                direction = direction,
                maxDistance = PlayerConstants.REACH_DISTANCE
            )
        )
    }

    @BusEvent
    fun onBlockRayCastResultDig(event: PhysicsEvent.OnRayCastBlockResult) {
        if (event.requestId != RayCastTypes.CHUNK_DIG_RAY_CAST) return

        val owner = event.requestOwner
        if (!holdAttackMapper.has(owner)) return
        val inventory = inventoryMapper[owner] ?: return

        if (event.blockToRemove == BlockType.AIR) {
            stopDigging(owner)
            return
        }

        lastHitTime.put(owner, time)

        val blockInfo = blockDataManager.getBlockInfoDataMap()[event.blockToRemove] ?: return
        val tool = inventory.getSelectedInventoryItem()?.item
        val toolType = tool?.toolType
        val toolLevel = tool?.toolLevel

        val existing = diggingMapper[owner]
        val sameTarget = existing != null &&
                existing.chunkPosition == event.chunkPosition &&
                existing.blockPosition == event.blockPosition &&
                existing.miningToolType == toolType &&
                existing.miningToolLevel == toolLevel

        if (sameTarget) return

        val digging = existing ?: diggingMapper.create(owner)

        digging.chunkPosition = event.chunkPosition
        digging.blockPosition = event.blockPosition
        digging.blockToSet = event.blockToSet
        digging.blockToRemove = event.blockToRemove
        digging.miningToolType = toolType
        digging.miningToolLevel = toolLevel

        var digTime = blockInfo.digTime
        if (toolType != null && blockInfo.digTool == toolType) {
            digTime /= 2f
            digTime /= (toolLevel ?: 1).coerceAtLeast(1)
        }

        digging.digTime = digTime.coerceAtLeast(0.001f)
        digging.process = 0f
    }

    private fun stopDigging(entityId: Int) {
        lastRayCastTime.remove(entityId)
        lastHitTime.remove(entityId)

        if (diggingMapper.has(entityId)) {
            diggingMapper.remove(entityId)
        }
    }

    override fun removed(entityId: Int) {
        lastRayCastTime.remove(entityId)
        lastHitTime.remove(entityId)
    }

    private companion object {
        const val HIT_TIMEOUT = 0.25f
        const val RAY_CAST_INTERVAL = 0.05f
    }
}