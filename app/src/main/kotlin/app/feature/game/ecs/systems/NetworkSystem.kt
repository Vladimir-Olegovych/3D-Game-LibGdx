package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
import app.feature.game.ecs.states.ClientNetworkState
import app.feature.game.ecs.states.NetworkOutboundState
import app.feature.game.ecs.states.OutboundEntityState
import app.feature.game.ecs.states.RemotePlayerRegistry
import app.feature.game.event.ClientEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.eventbus.annotation.EventType
import com.gigapi.mesh.ModelAssetManager
import com.gigcreator.NetEntityType
import com.gigcreator.NetQuaternion
import com.gigcreator.NetVector3
import com.gigcreator.NetworkEvent
import core.animator.ModelAnimator
import core.assets.ModelID
import core.defaults.WorldConstants
import core.mesh.defaultPlayerHitBox
import core.mesh.rawMeshParams
import core.network.*
import kotlin.math.cos
import kotlin.math.sin

class NetworkSystem: BaseSystem() {

    companion object {
        private const val MOVE_EPS2 = 0.05f * 0.05f
    }

    @Wire
    private lateinit var networkStateUpdater: NetworkStateUpdater
    @Wire
    private lateinit var networkState: ClientNetworkState
    @Wire
    private lateinit var outboundState: NetworkOutboundState
    @Wire
    private lateinit var remotePlayerRegistry: RemotePlayerRegistry
    @Wire
    private lateinit var modelAssetManager: ModelAssetManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var animatorMapper: ComponentMapper<AnimatorComponent>
    private lateinit var lookDirectionMapper: ComponentMapper<LookDirectionComponent>
    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var networkEntityMapper: ComponentMapper<NetworkEntityComponent>
    private lateinit var interpolationMapper: ComponentMapper<NetworkInterpolationComponent>

    override fun initialize() {
        networkStateUpdater.start()
    }

    @BusEvent
    fun onConnected(event: ClientEvent.OnConnected) {
        networkState.connection = event.connection
    }

    @BusEvent
    fun onDisconnected(event: ClientEvent.OnDisconnected) {
        networkState.connection = null
        outboundState.clear()
    }

    @BusEvent
    @EventType(NetworkEvent.HelloFromServer::class)
    fun onHelloFromServer(received: ClientEvent.OnReceived) {
        val hello = received.event as NetworkEvent.HelloFromServer
        networkState.localPlayerId = hello.playerId

        val localEntityId = WorldConstants.getLocalPlayerEntityId()
        networkEntityMapper[localEntityId]?.networkId = hello.playerId
    }

    @BusEvent
    @EventType(NetworkEvent.EntityJoined::class)
    fun onEntityJoined(received: ClientEvent.OnReceived) {
        val event = received.event as NetworkEvent.EntityJoined
        if (event.entityType != NetEntityType.PLAYER) return
        spawnRemotePlayer(event.entityId, event.pos, NetQuaternion.identity(), event.modelId)
    }

    @BusEvent
    @EventType(NetworkEvent.EntityLeft::class)
    fun onEntityLeft(received: ClientEvent.OnReceived) {
        val event = received.event as NetworkEvent.EntityLeft
        if (event.entityType != NetEntityType.PLAYER) return
        despawnRemotePlayer(event.entityId)
    }

    @BusEvent
    @EventType(NetworkEvent.EntityStateUpdate::class)
    fun onEntityStateUpdate(received: ClientEvent.OnReceived) {
        val event = received.event as NetworkEvent.EntityStateUpdate
        if (event.entityType != NetEntityType.PLAYER) return
        updateRemotePlayer(event.entityId, event.pos, event.rot, event.modelId)
    }

    @BusEvent
    @EventType(NetworkEvent.EntityStateSnapshot::class)
    fun onEntityStateSnapshot(received: ClientEvent.OnReceived) {
        val event = received.event as NetworkEvent.EntityStateSnapshot
        if (event.entityType != NetEntityType.PLAYER) return
        if (remotePlayerRegistry.networkIdToEntityId.containsKey(event.entityId)) {
            updateRemotePlayer(event.entityId, event.pos, event.rot, event.modelId)
        } else {
            spawnRemotePlayer(event.entityId, event.pos, event.rot, event.modelId)
        }
    }

    override fun processSystem() {
        interpolateRemotes(world.delta)
        sendLocalState()
    }

    override fun dispose() {
        networkStateUpdater.stop()
        outboundState.clear()
    }

    private fun sendLocalState() {
        if (networkState.localPlayerId < 0) return

        val localEntityId = WorldConstants.getLocalPlayerEntityId()
        val networkEntity = networkEntityMapper[localEntityId] ?: return
        if (networkEntity.networkId != networkState.localPlayerId) {
            networkEntity.networkId = networkState.localPlayerId
        }

        val transform = transformMapper[localEntityId]?.transform ?: return
        val look = lookDirectionMapper[localEntityId] ?: return
        val position = Vector3()
        transform.getTranslation(position)
        val rot = lookToNetQuaternion(look.yaw, look.pitch)

        outboundState.put(
            OutboundEntityState(
                entityId = networkState.localPlayerId,
                entityType = NetEntityType.PLAYER,
                modelId = networkEntity.modelId,
                x = position.x,
                y = position.y,
                z = position.z,
                qx = rot.x,
                qy = rot.y,
                qz = rot.z,
                qw = rot.w,
            )
        )
    }

    private fun interpolateRemotes(delta: Float) {
        for (entityId in remotePlayerRegistry.networkIdToEntityId.values) {
            val interp = interpolationMapper[entityId] ?: continue
            if (!interp.hasTarget) continue

            val transformComponent = transformMapper[entityId] ?: continue
            val transform = transformComponent.transform ?: Matrix4().also { transformComponent.transform = it }

            interp.elapsed = (interp.elapsed + delta).coerceAtMost(interp.duration)
            val alpha = if (interp.duration <= 0f) 1f else (interp.elapsed / interp.duration).coerceIn(0f, 1f)

            interp.renderPos.set(interp.fromPos).lerp(interp.toPos, alpha)
            interp.renderRot.set(interp.fromRot).slerp(interp.toRot, alpha)

            transform.idt().setTranslation(interp.renderPos)
            applyLookDirection(entityId, interp.renderRot.toYawDegrees(), interp.renderRot.toPitchDegrees())
            updateRemoteAnimation(entityId, isMoving(interp))
        }
    }

    private fun spawnRemotePlayer(networkId: Int, pos: NetVector3, rot: NetQuaternion, modelId: Int) {
        if (networkId == networkState.localPlayerId) return
        if (remotePlayerRegistry.networkIdToEntityId.containsKey(networkId)) return

        val entityId = world.create()
        remotePlayerRegistry.networkIdToEntityId[networkId] = entityId

        networkEntityMapper.create(entityId).apply {
            this.networkId = networkId
            this.entityType = NetEntityType.PLAYER
            this.isLocal = false
            this.modelId = modelId
        }

        val interp = interpolationMapper.create(entityId)
        resetInterpolation(interp, pos, rot)
        transformMapper.create(entityId).transform = Matrix4().setTranslation(interp.renderPos)
        lookDirectionMapper.create(entityId)
        applyLookDirection(entityId, rot.toYawDegrees(), rot.toPitchDegrees())
        applyVisual(entityId, modelId)
        updateRemoteAnimation(entityId, moving = false)
    }

    private fun updateRemotePlayer(networkId: Int, pos: NetVector3, rot: NetQuaternion, modelId: Int) {
        if (networkId == networkState.localPlayerId) return

        val entityId = remotePlayerRegistry.networkIdToEntityId[networkId]
            ?: run {
                spawnRemotePlayer(networkId, pos, rot, modelId)
                return
            }

        val interp = interpolationMapper[entityId] ?: interpolationMapper.create(entityId)
        pushInterpolationTarget(interp, pos, rot)

        val networkEntity = networkEntityMapper[entityId] ?: return
        if (networkEntity.modelId != modelId) {
            networkEntity.modelId = modelId
            applyVisual(entityId, modelId)
        }
        updateRemoteAnimation(entityId, isMoving(interp))
    }

    private fun resetInterpolation(interp: NetworkInterpolationComponent, pos: NetVector3, rot: NetQuaternion) {
        interp.fromPos.set(pos.x, pos.y, pos.z)
        interp.toPos.set(pos.x, pos.y, pos.z)
        interp.fromRot.setFromNet(rot)
        interp.toRot.setFromNet(rot)
        interp.renderPos.set(interp.toPos)
        interp.renderRot.set(interp.toRot)
        interp.elapsed = interp.duration
        interp.hasTarget = true
    }

    private fun pushInterpolationTarget(interp: NetworkInterpolationComponent, pos: NetVector3, rot: NetQuaternion) {
        if (interp.hasTarget) {
            val alpha = if (interp.duration <= 0f) 1f else (interp.elapsed / interp.duration).coerceIn(0f, 1f)
            interp.fromPos.set(interp.fromPos).lerp(interp.toPos, alpha)
            interp.fromRot.set(interp.fromRot).slerp(interp.toRot, alpha)
        } else {
            interp.fromPos.set(pos.x, pos.y, pos.z)
            interp.fromRot.setFromNet(rot)
        }

        interp.toPos.set(pos.x, pos.y, pos.z)
        interp.toRot.setFromNet(rot)

        if (interp.fromPos.dst2(interp.toPos) > NetworkInterpolationComponent.SNAP_DISTANCE * NetworkInterpolationComponent.SNAP_DISTANCE) {
            interp.fromPos.set(interp.toPos)
            interp.fromRot.set(interp.toRot)
        }

        interp.elapsed = 0f
        interp.duration = NetworkInterpolationComponent.DEFAULT_DURATION
        interp.hasTarget = true
    }

    private fun applyLookDirection(entityId: Int, yaw: Float, pitch: Float) {
        val look = lookDirectionMapper[entityId] ?: return
        look.yaw = yaw
        look.pitch = pitch

        val pitchRad = Math.toRadians(pitch.toDouble())
        val yawRad = Math.toRadians(yaw.toDouble())
        look.direction.set(
            (cos(pitchRad) * sin(yawRad)).toFloat(),
            sin(pitchRad).toFloat(),
            (cos(pitchRad) * cos(yawRad)).toFloat()
        ).nor()
    }

    private fun updateRemoteAnimation(entityId: Int, moving: Boolean) {
        val animator = animatorMapper[entityId]?.animator ?: return
        animator.playAnimation(if (moving) ModelAnimator.ANIM_MOVE else ModelAnimator.ANIM_IDLE)
    }

    private fun isMoving(interp: NetworkInterpolationComponent): Boolean {
        return interp.fromPos.dst2(interp.toPos) > MOVE_EPS2
    }

    private fun applyVisual(entityId: Int, modelId: Int) {
        clearVisual(entityId)

        val model = resolveModelId(modelId)
        if (model == ModelID.NULL) {
            meshMapper.create(entityId).meshData = defaultPlayerHitBox.createMeshData(rawMeshParams)
            boundMapper.create(entityId).boundingRadius = 1.8f
            return
        }

        val blenderModel = modelAssetManager.getRenderModel(model)
        if (model == ModelID.M_PLAYER_MODEL) {
            blenderModel.subMeshes.forEach {
                it.mesh.transform(Matrix4().translate(0F, 0F, 0F))
                it.mesh.scale(0.35f, 0.35f, 0.35f)
            }
            animatorMapper.create(entityId).animator = ModelAnimator(blenderModel)
        }
        blenderMapper.create(entityId).blenderRenderData = blenderModel
        boundMapper.create(entityId).boundingRadius = 1.8f
    }

    private fun clearVisual(entityId: Int) {
        meshMapper[entityId]?.dispose()
        meshMapper.remove(entityId)
        animatorMapper.remove(entityId)
        blenderMapper[entityId]?.dispose()
        blenderMapper.remove(entityId)
        boundMapper.remove(entityId)
    }

    private fun resolveModelId(modelId: Int): ModelID {
        return ModelID.entries.getOrNull(modelId) ?: ModelID.NULL
    }

    private fun despawnRemotePlayer(networkId: Int) {
        val entityId = remotePlayerRegistry.networkIdToEntityId.remove(networkId) ?: return
        clearVisual(entityId)
        world.delete(entityId)
    }
}
