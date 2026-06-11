package core.bullet.world

import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import core.bullet.PhysicalData
import core.bullet.PhysicsUtils
import kotlinx.coroutines.Dispatchers
import kotlin.collections.iterator

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
            hitEntityId = physicBodies.entries.find {
                it.value.getBody() == hitCollisionObject
            }?.key
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

    @BusEvent
    fun onUpdateChunkData(event: GameEvent.OnUpdateChunkData) {
        val entityId = event.chunkEntityId
        val physicalData = PhysicsUtils.createChunkBody(event.chunkData)
        physicBodies[entityId]?.let {
            it.getBodyNullable()?.let { body -> physicsWorld.world.removeRigidBody(body) }
            it.dispose()
        }
        physicBodies[entityId] = physicalData
        physicsWorld.world.addRigidBody(physicalData.getBody())
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
        val physicalData = physicBodies[entityId]?: return
        physicBodies.remove(entityId)
        physicsWorld.world.removeRigidBody(physicalData.getBody())
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