package core.network

import app.feature.game.ecs.states.NetworkOutboundState
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.kryo.GameClient
import com.gigcreator.GamePacket
import com.gigcreator.NetQuaternion
import com.gigcreator.NetVector3
import com.gigcreator.NetworkEvent
import kotlinx.coroutines.Dispatchers

class NetworkStateUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 20F, Dispatchers.IO) {

    private lateinit var gameClient: GameClient
    private lateinit var outboundState: NetworkOutboundState

    private var tick: Long = 0

    override fun launch(gContext: GContext) {
        gameClient = gContext.getObject()
        outboundState = gContext.getObject()
    }

    override fun create() {}

    override fun update(deltaTime: Float) {
        val states = outboundState.snapshot()
        if (states.isEmpty()) return

        val currentTick = tick++
        val events = Array<NetworkEvent>(states.size) { index ->
            val state = states[index]
            NetworkEvent.EntityStateUpdate(
                entityId = state.entityId,
                entityType = state.entityType,
                pos = NetVector3(state.x, state.y, state.z),
                rot = NetQuaternion(state.qx, state.qy, state.qz, state.qw),
                tick = currentTick,
                modelId = state.modelId,
            )
        }

        gameClient.sendUDP(GamePacket(events = events))
    }

    override fun dispose() {
        tick = 0
    }
}
