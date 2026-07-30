package app.feature.game.ecs.systems

import app.feature.game.ecs.components.*
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
import core.assets.ModelID
import core.controls.PlayerInputProcessor
import core.defaults.WorldConstants
import core.mesh.defaultPlayerHitBox
import core.mesh.rawMeshParams
import core.network.*

class NetworkSystem: BaseSystem() {

    @Wire
    private lateinit var networkStateUpdater: NetworkStateUpdater
    @Wire
    private lateinit var networkState: ClientNetworkState
    @Wire
    private lateinit var outboundState: NetworkOutboundState
    @Wire
    private lateinit var remotePlayerRegistry: RemotePlayerRegistry
    @Wire
    private lateinit var playerInputProcessor: PlayerInputProcessor
    @Wire
    private lateinit var modelAssetManager: ModelAssetManager

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var blenderMapper: ComponentMapper<BlenderModelComponent>
    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var networkEntityMapper: ComponentMapper<NetworkEntityComponent>

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
        if (networkState.localPlayerId < 0) return

        val localEntityId = WorldConstants.getLocalPlayerEntityId()
        val networkEntity = networkEntityMapper[localEntityId] ?: return
        if (networkEntity.networkId != networkState.localPlayerId) {
            networkEntity.networkId = networkState.localPlayerId
        }

        val transform = transformMapper[localEntityId]?.transform ?: return
        val position = Vector3()
        transform.getTranslation(position)
        val rot = yawToNetQuaternion(playerInputProcessor.getYaw())

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

    override fun dispose() {
        networkStateUpdater.stop()
        outboundState.clear()
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

        transformMapper.create(entityId).transform = Matrix4().setFromNetTransform(pos, rot)
        applyVisual(entityId, modelId)
    }

    private fun updateRemotePlayer(networkId: Int, pos: NetVector3, rot: NetQuaternion, modelId: Int) {
        if (networkId == networkState.localPlayerId) return

        val entityId = remotePlayerRegistry.networkIdToEntityId[networkId]
            ?: run {
                spawnRemotePlayer(networkId, pos, rot, modelId)
                return
            }

        transformMapper[entityId]?.transform = Matrix4().setFromNetTransform(pos, rot)

        val networkEntity = networkEntityMapper[entityId] ?: return
        if (networkEntity.modelId != modelId) {
            networkEntity.modelId = modelId
            applyVisual(entityId, modelId)
        }
    }

    private fun applyVisual(entityId: Int, modelId: Int) {
        clearVisual(entityId)

        val model = resolveModelId(modelId)
        if (model == ModelID.NULL) {
            meshMapper.create(entityId).meshData = defaultPlayerHitBox.createMeshData(rawMeshParams)
            boundMapper.create(entityId).boundingRadius = 1.8f
            return
        }

        blenderMapper.create(entityId).blenderRenderData = modelAssetManager.getRenderModel(model)
        boundMapper.create(entityId).boundingRadius = 1.8f
    }

    private fun clearVisual(entityId: Int) {
        meshMapper[entityId]?.dispose()
        meshMapper.remove(entityId)
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
