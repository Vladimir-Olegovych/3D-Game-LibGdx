package core.bullet.world

import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import core.bullet.PhysicalData
import core.bullet.PhysicsUtils
import kotlinx.coroutines.Dispatchers

class PhysicsWorldUpdater: LaunchedEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    private lateinit var physicalEventBus: EventBus
    private lateinit var mainEventBus: EventBus
    private lateinit var physicsWorld: PhysicsWorld
    private lateinit var physicBodies: HashMap<Int, PhysicalData>

    override fun launch(context: GContext) {
        physicalEventBus = context.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        mainEventBus = context.getObject(EventBusTypes.MAIN_EVENT_BUS)
        physicalEventBus.registerHandler(this)
    }

    @BusEvent
    fun onRayCastRequest(event: GameEvent.OnRayCastRequest) {
        val rayFrom = event.from
        val rayTo = Vector3(event.direction).scl(event.maxDistance).add(rayFrom)
        val callback = ClosestRayResultCallback(rayFrom, rayTo)
        physicsWorld.world.rayTest(rayFrom, rayTo, callback)

        var hitEntityId: Int? = null
        val hitPoint = Vector3()
        val hitNormal = Vector3()

        if (callback.hasHit()) {
            callback.getHitPointWorld(hitPoint)
            callback.getHitNormalWorld(hitNormal)

            val hitCollisionObject = callback.collisionObject
            hitEntityId = hitCollisionObject.userData as? Int
            if (hitEntityId == 0) hitEntityId = null
        }

        mainEventBus.sendEvent(
            GameEvent.OnRayCastResult(
                requestId = event.requestId,
                direction = event.direction,
                hasHit = callback.hasHit(),
                hitPoint = hitPoint.cpy(),
                hitNormal = hitNormal.cpy(),
                hitEntityId = hitEntityId
            )
        )

        callback.dispose()
    }

    private val lastUpdate = HashMap<Int, GameEvent.OnUpdateChunkData>()

    @BusEvent
    fun onUpdateChunkData(event: GameEvent.OnUpdateChunkData) {
        val entityId = event.chunkEntityId
        lastUpdate[entityId] = event
        val physicalData = physicBodies.remove(entityId) ?: return
        val body = physicalData.getBodyOrNull() ?: return
        if (!body.isDisposed) {
            physicsWorld.world.removeRigidBody(body)
        }
        physicalData.dispose()
    }

    fun updateChunkImmediately(event: GameEvent.OnUpdateChunkData) {
        val entityId = event.chunkEntityId
        val physicalData = PhysicsUtils.createChunkBody(entityId = entityId, event.chunkData)
        physicBodies[entityId] = physicalData
        physicsWorld.world.addRigidBody(physicalData.getBody())
    }

    @BusEvent
    fun onChunkBodyCreated(event: GameEvent.OnCreateChunkRigidBody) {
        val entityId = event.chunkEntityId
        val physicalData = PhysicsUtils.createChunkBody(entityId = entityId,event.chunkData)
        physicBodies[entityId] = physicalData
        physicsWorld.world.addRigidBody(physicalData.getBody())
    }

    @BusEvent
    fun onMeshBodyCreated(event: GameEvent.OnCreateMeshRigidBody) {
        val entityId = event.entityId
        val physicalData = PhysicsUtils.createMeshBody(
            entityId = entityId,
            position = event.position,
            rawMeshData = event.rawMeshData,
            mass = event.mass,
            activationState = event.activationState,
            friction = event.friction,
            restitution = event.restitution,
            fixedXZ = event.fixedXZ
        )
        physicBodies[entityId] = physicalData
        physicsWorld.world.addRigidBody(physicalData.getBody())
    }

    @BusEvent
    fun onBodyRemoved(event: GameEvent.OnRemoveRigidBody) {
        val entityId = event.entityId
        val physicalData = physicBodies.remove(entityId) ?: return
        val body = physicalData.getBodyOrNull() ?: return
        if (!body.isDisposed) {
            physicsWorld.world.removeRigidBody(body)
        }
        physicalData.dispose()
    }

    @BusEvent
    fun onApplyForce(event: GameEvent.OnApplyForce) {
        val entityId = event.entityId
        val physicalData = physicBodies[entityId]?: return
        physicalData.getBody().applyCentralForce(event.force)
    }

    @BusEvent
    fun onApplyLinearForce(event: GameEvent.OnApplyLinearForce) {
        val entityId = event.entityId
        val physicalData = physicBodies[entityId]?: return
        val body = physicalData.getBody()
        val currentLinearVelocity = body.linearVelocity
        if (event.ignoreYLinear) {
            body.linearVelocity = Vector3(event.force.x, currentLinearVelocity.y, event.force.z)
        } else {
            body.linearVelocity = Vector3(event.force.x, event.force.y, event.force.z)
        }
    }

    override fun create() {
        Bullet.init()
        physicBodies = HashMap()
        physicsWorld = PhysicsWorld()
    }
    override fun update(deltaTime: Float) {
        physicsWorld.update(deltaTime)
        physicalEventBus.process()
        lastUpdate.forEach { (_, data) -> updateChunkImmediately(data) }
        lastUpdate.clear()
        for ((entityId, data) in physicBodies) {
            if (data.isStatic) continue
            val body = data.getBody()
            if (!body.isActive) continue
            mainEventBus.sendEvent(GameEvent.OnRigidBodyTransformUpdate(entityId, body.worldTransform.cpy()))
        }
    }

    override fun dispose() {
        for ((_, data) in physicBodies) {
            val body = data.getBodyOrNull()
            if (body != null && !body.isDisposed) {
                physicsWorld.world.removeRigidBody(body)
            }
            data.dispose()
        }
        physicBodies.clear()
        physicalEventBus.clear()
        physicsWorld.dispose()
    }

}