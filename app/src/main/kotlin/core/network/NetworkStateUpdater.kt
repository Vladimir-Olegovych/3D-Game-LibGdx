package core.network

import app.feature.game.ecs.states.OutboundNetworkState
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.kryo.GameClient
import com.gigcreator.GamePacket
import com.gigcreator.NetQuaternion
import com.gigcreator.NetVector3
import com.gigcreator.NetworkEvent
import com.gigcreator.network.SendContainer
import com.gigcreator.network.SendType
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class NetworkStateUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 20F, Dispatchers.IO) {

    private companion object {
        private const val MAX_EVENTS_PER_PACKET = 32
    }

    private lateinit var gameClient: GameClient
    private lateinit var outboundState: OutboundNetworkState

    private val sendTasks = ArrayList<Deferred<Any?>>()

    override fun launch(gContext: GContext) {
        gameClient = gContext.getObject()
        outboundState = gContext.getObject()
    }

    override fun create() {}

    override fun update(deltaTime: Float) {
        runBlocking {
            sendTasks.forEach { it.await() }
            sendTasks.clear()
        }

        val tcpEvents = outboundState.drainTCP()
        val udpEvents = outboundState.replaceUDP()

        sendBatched(tcpEvents, SendType.TCP)
        sendBatched(udpEvents, SendType.UDP)
    }

    private fun sendBatched(events: Array<NetworkEvent>, sendType: SendType) {
        if (events.isEmpty()) return

        var start = 0
        while (start < events.size) {
            val end = minOf(start + MAX_EVENTS_PER_PACKET, events.size)
            val chunk = events.copyOfRange(start, end)

            sendPacket(SendContainer(GamePacket(chunk), sendType))
            start = end
        }
    }

    private fun sendPacket(packet: SendContainer<GamePacket>) {
        val deferred = lifecycleScope.async {
            try {
                when (packet.sendType) {
                    SendType.TCP -> gameClient.sendTCP(packet.data)
                    SendType.UDP -> gameClient.sendUDP(packet.data)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        sendTasks.add(deferred)
    }

    override fun dispose() {}
}