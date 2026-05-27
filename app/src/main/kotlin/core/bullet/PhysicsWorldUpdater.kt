package core.bullet

import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.badlogic.gdx.physics.bullet.Bullet
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import kotlinx.coroutines.Dispatchers

class PhysicsWorldUpdater: LaunchedEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    private lateinit var physicalEventBus: EventBus
    private lateinit var mainEventBus: EventBus
    private lateinit var physicsWorld: PhysicsWorld
    private lateinit var physicBodies: HashMap<Int, PhysicalData>

    override fun launch(context: Context) {
        physicalEventBus = context.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        mainEventBus = context.getObject(EventBusTypes.MAIN_EVENT_BUS)
        physicalEventBus.registerHandler(this)
    }

    @BusEvent
    fun onChunkBodyCreated(event: GameEvent.OnCreateChunkRigidBody) {
        val entityId = event.chunkEntityId
        val physicalData = PhysicsUtils.createChunkBody(event.chunkData)
        physicBodies[entityId] = physicalData
        physicsWorld.world.addRigidBody(physicalData.getBody())
    }

    @BusEvent
    fun onMeshBodyCreated(event: GameEvent.OnCreateMeshRigidBody) {
        val entityId = event.entityId
        val physicalData = PhysicsUtils.createMeshBody(
            position = event.position,
            rawMeshData = event.rawMeshData,
            mass = event.mass
        )
        physicBodies[entityId] = physicalData
        physicsWorld.world.addRigidBody(physicalData.getBody())
    }

    @BusEvent
    fun onBodyRemoved(event: GameEvent.OnRemoveRigidBody) {
        val entityId = event.entityId
        val physicalData = physicBodies[entityId]?: return
        physicBodies.remove(entityId)
        physicsWorld.world.removeRigidBody(physicalData.getBody())
        physicalData.dispose()
    }

    override fun create() {
        Bullet.init()
        physicBodies = HashMap()
        physicsWorld = PhysicsWorld()
    }
    override fun update(deltaTime: Float) {
        physicsWorld.update(deltaTime)
        physicalEventBus.process()
        for ((entityId, data) in physicBodies) {
            if (data.isStatic) continue
            val body = data.getBody()
            if (!body.isActive) continue
            mainEventBus.sendEvent(GameEvent.OnRigidBodyTransformUpdate(entityId, body.worldTransform.cpy()))
        }
    }

    override fun dispose() {
        for ((_, data) in physicBodies) {
            val body = data.getBody()
            physicsWorld.world.removeRigidBody(body)
            data.dispose()
        }
        physicBodies.clear()
        physicalEventBus.clear()
        physicsWorld.dispose()
    }

}