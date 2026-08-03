package core.bullet.world

import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseProxy
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import core.bullet.PhysicalData
import core.bullet.PhysicsUtils
import core.chunk.ChunkData
import core.defaults.WorldConstants
import kotlinx.coroutines.Dispatchers

class PhysicsWorldUpdater: LaunchedEffect, DeltaUpdater(1 / 30F, Dispatchers.Default) {

    private lateinit var physicalEventBus: EventBus
    private lateinit var mainEventBus: EventBus
    private lateinit var physicsWorld: PhysicsWorld
    private lateinit var physicBodies: HashMap<Int, PhysicalData>

    private val pendingChunkBodies = HashMap<Int, ChunkData>()
    private val linearIntents = HashMap<Int, LinearIntent>()
    private val forceIntents = HashMap<Int, ForceIntent>()

    private val tmpVelocity = Vector3()
    private val tmpImpulse = Vector3()

    override fun launch(gContext: GContext) {
        physicalEventBus = gContext.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        mainEventBus = gContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        physicalEventBus.registerHandler(this)
    }

    @BusEvent
    fun onRayCastRequest(event: GameEvent.OnRayCastRequest) {
        val rayFrom = event.from
        val rayTo = Vector3(event.direction).scl(event.maxDistance).add(rayFrom)
        val ignoreEntityId = WorldConstants.getLocalPlayerEntityId()
        val callback = IgnoreEntityRayCallback(rayFrom, rayTo, ignoreEntityId)
        physicsWorld.world.rayTest(rayFrom, rayTo, callback)

        var hitEntityId: Int? = null
        val hitPoint = Vector3()
        val hitNormal = Vector3()

        if (callback.hasHit()) {
            callback.getHitPointWorld(hitPoint)
            callback.getHitNormalWorld(hitNormal)

            val hitCollisionObject = callback.collisionObject
            hitEntityId = hitCollisionObject?.userData as? Int
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

    @BusEvent
    fun onUpdateChunkData(event: GameEvent.OnUpdateChunkData) {
        // Only queue — never remove/dispose here. Raycasts in the same process()
        // batch must still see the old body; replace happens after process().
        pendingChunkBodies[event.chunkEntityId] = event.chunkData
    }

    @BusEvent
    fun onChunkBodyCreated(event: GameEvent.OnCreateChunkRigidBody) {
        pendingChunkBodies[event.chunkEntityId] = event.chunkData
    }

    @BusEvent
    fun onMeshBodyCreated(event: GameEvent.OnCreateMeshRigidBody) {
        val entityId = event.entityId
        removeBody(entityId)
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
        pendingChunkBodies.remove(entityId)
        linearIntents.remove(entityId)
        forceIntents.remove(entityId)
        removeBody(entityId)
    }

    @BusEvent
    fun onApplyForce(event: GameEvent.OnApplyForce) {
        val intent = forceIntents.getOrPut(event.entityId) { ForceIntent() }
        intent.force.set(event.force)
        intent.active = true
    }

    @BusEvent
    fun onApplyLinearForce(event: GameEvent.OnApplyLinearForce) {
        val intent = linearIntents.getOrPut(event.entityId) { LinearIntent() }
        intent.velocity.set(event.force)
        intent.ignoreYLinear = event.ignoreYLinear
        intent.active = true
    }

    override fun create() {
        Bullet.init()
        physicBodies = HashMap()
        physicsWorld = PhysicsWorld()
    }

    override fun update(deltaTime: Float) {
        physicalEventBus.process()
        flushPendingChunkBodies()

        applyMovementIntents(deltaTime)
        physicsWorld.update(deltaTime)

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
        linearIntents.clear()
        forceIntents.clear()
        pendingChunkBodies.clear()
        physicalEventBus.clear()
        physicsWorld.dispose()
    }

    private fun flushPendingChunkBodies() {
        if (pendingChunkBodies.isEmpty()) return
        val pending = HashMap(pendingChunkBodies)
        pendingChunkBodies.clear()
        for ((entityId, chunkData) in pending) {
            replaceChunkBody(entityId, chunkData)
        }
    }

    private fun replaceChunkBody(entityId: Int, chunkData: ChunkData) {
        removeBody(entityId)
        val physicalData = PhysicsUtils.createChunkBody(entityId, chunkData)
        physicBodies[entityId] = physicalData
        physicsWorld.world.addRigidBody(physicalData.getBody())
    }

    private fun removeBody(entityId: Int) {
        val physicalData = physicBodies.remove(entityId) ?: return
        try {
            val body = physicalData.getBodyOrNull()
            if (body != null && !body.isDisposed) {
                physicsWorld.world.removeRigidBody(body)
            }
        } finally {
            try {
                physicalData.dispose()
            } catch (_: Throwable) {
            }
        }
    }

    private fun applyMovementIntents(deltaTime: Float) {
        for ((entityId, intent) in linearIntents) {
            if (!intent.active) continue
            val body = physicBodies[entityId]?.getBodyOrNull() ?: continue
            if (body.isDisposed) continue

            if (intent.ignoreYLinear) {
                val current = body.linearVelocity
                tmpVelocity.set(intent.velocity.x, current.y, intent.velocity.z)
            } else {
                tmpVelocity.set(intent.velocity)
            }
            body.linearVelocity = tmpVelocity
        }

        for ((entityId, intent) in forceIntents) {
            if (!intent.active) continue
            val body = physicBodies[entityId]?.getBodyOrNull() ?: continue
            if (body.isDisposed) continue
            if (intent.force.isZero) continue

            tmpImpulse.set(intent.force).scl(deltaTime)
            body.applyCentralImpulse(tmpImpulse)
        }
    }

    private class LinearIntent {
        val velocity = Vector3()
        var ignoreYLinear = false
        var active = false
    }

    private class ForceIntent {
        val force = Vector3()
        var active = false
    }

    private class IgnoreEntityRayCallback(
        rayFrom: Vector3,
        rayTo: Vector3,
        private val ignoreEntityId: Int
    ) : ClosestRayResultCallback(rayFrom, rayTo) {
        override fun needsCollision(proxy0: btBroadphaseProxy): Boolean {
            if (!super.needsCollision(proxy0)) return false
            val obj = btCollisionObject.getInstance(proxy0.clientObject, false) ?: return true
            return obj.userData != ignoreEntityId
        }
    }
}
