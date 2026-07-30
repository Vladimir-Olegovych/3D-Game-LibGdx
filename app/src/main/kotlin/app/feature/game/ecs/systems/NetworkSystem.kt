package app.feature.game.ecs.systems

import app.feature.game.ecs.components.BoundRadiusComponent
import app.feature.game.ecs.components.MeshComponent
import app.feature.game.ecs.components.NetworkEntityComponent
import app.feature.game.ecs.components.TransformComponent
import app.feature.game.ecs.states.RemotePlayerRegistry
import app.feature.game.event.ClientEvent
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.Matrix4
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.eventbus.annotation.EventType
import com.gigapi.kryo.GameClient
import com.gigcreator.GamePacket
import com.gigcreator.NetEntityType
import com.gigcreator.NetworkEvent
import core.controls.PlayerInputProcessor
import core.defaults.WorldConstants
import core.mesh.MeshUtils
import core.mesh.rawMeshParams
import core.network.ClientNetworkState
import core.network.setFromNetTransform
import core.network.toNetVector3
import core.network.yawToNetQuaternion

class NetworkSystem: BaseSystem() {

    @Wire
    private lateinit var gameClient: GameClient
    @Wire
    private lateinit var networkState: ClientNetworkState
    @Wire
    private lateinit var remotePlayerRegistry: RemotePlayerRegistry
    @Wire
    private lateinit var playerInputProcessor: PlayerInputProcessor

    private lateinit var transformMapper: ComponentMapper<TransformComponent>
    private lateinit var meshMapper: ComponentMapper<MeshComponent>
    private lateinit var boundMapper: ComponentMapper<BoundRadiusComponent>
    private lateinit var networkEntityMapper: ComponentMapper<NetworkEntityComponent>

    private var tick: Long = 0

    @BusEvent
    fun onConnected(event: ClientEvent.OnConnected) {
        networkState.connection = event.connection
    }

    @BusEvent
    fun onDisconnected(event: ClientEvent.OnDisconnected) {
        networkState.connection = null
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
        spawnRemotePlayer(event.entityId, event.pos, com.gigcreator.NetQuaternion.identity())
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
        updateRemotePlayer(event.entityId, event.pos, event.rot)
    }

    @BusEvent
    @EventType(NetworkEvent.EntityStateSnapshot::class)
    fun onEntityStateSnapshot(received: ClientEvent.OnReceived) {
        val event = received.event as NetworkEvent.EntityStateSnapshot
        if (event.entityType != NetEntityType.PLAYER) return
        if (remotePlayerRegistry.networkIdToEntityId.containsKey(event.entityId)) {
            updateRemotePlayer(event.entityId, event.pos, event.rot)
        } else {
            spawnRemotePlayer(event.entityId, event.pos, event.rot)
        }
    }

    override fun processSystem() {
        if (networkState.localPlayerId < 0) return

        val localEntityId = WorldConstants.getLocalPlayerEntityId()
        networkEntityMapper[localEntityId]?.let {
            if (it.networkId != networkState.localPlayerId) it.networkId = networkState.localPlayerId
        }

        val transform = transformMapper[localEntityId]?.transform ?: return
        val position = com.badlogic.gdx.math.Vector3()
        transform.getTranslation(position)

        gameClient.sendUDP(
            GamePacket(
                events = arrayOf(
                    NetworkEvent.EntityStateUpdate(
                        entityId = networkState.localPlayerId,
                        entityType = NetEntityType.PLAYER,
                        pos = position.toNetVector3(),
                        rot = yawToNetQuaternion(playerInputProcessor.getYaw()),
                        tick = tick++,
                    )
                )
            )
        )
    }

    private fun spawnRemotePlayer(networkId: Int, pos: com.gigcreator.NetVector3, rot: com.gigcreator.NetQuaternion) {
        if (networkId == networkState.localPlayerId) return
        if (remotePlayerRegistry.networkIdToEntityId.containsKey(networkId)) return

        val entityId = world.create()
        remotePlayerRegistry.networkIdToEntityId[networkId] = entityId

        networkEntityMapper.create(entityId).apply {
            this.networkId = networkId
            this.entityType = NetEntityType.PLAYER
            this.isLocal = false
        }

        transformMapper.create(entityId).transform = Matrix4().setFromNetTransform(pos, rot)

        val hitboxModel = MeshUtils.createHitboxModel(1F, 1.8F)
        meshMapper.create(entityId).meshData = hitboxModel.createMeshData(rawMeshParams)
        boundMapper.create(entityId).boundingRadius = 1.8f
    }

    private fun updateRemotePlayer(networkId: Int, pos: com.gigcreator.NetVector3, rot: com.gigcreator.NetQuaternion) {
        if (networkId == networkState.localPlayerId) return

        val entityId = remotePlayerRegistry.networkIdToEntityId[networkId]
            ?: run {
                spawnRemotePlayer(networkId, pos, rot)
                return
            }

        transformMapper[entityId]?.transform = Matrix4().setFromNetTransform(pos, rot)
    }

    private fun despawnRemotePlayer(networkId: Int) {
        val entityId = remotePlayerRegistry.networkIdToEntityId.remove(networkId) ?: return
        meshMapper[entityId]?.dispose()
        world.delete(entityId)
    }
}
