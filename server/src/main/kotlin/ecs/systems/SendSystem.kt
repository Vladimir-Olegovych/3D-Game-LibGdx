package com.gigcreator.ecs.systems

import com.artemis.ComponentMapper
import com.artemis.annotations.All
import com.artemis.systems.IteratingSystem
import com.esotericsoftware.kryonet.Connection
import com.gigcreator.GamePacket
import com.gigcreator.ecs.components.ClientComponent
import com.gigcreator.network.SendContainer
import com.gigcreator.network.SendType
import kotlinx.coroutines.*

@All(ClientComponent::class)
class SendSystem: IteratingSystem() {

    private lateinit var clientComponentMapper: ComponentMapper<ClientComponent>
    private val scope = CoroutineScope(Dispatchers.IO)
    private val sendTasks = ArrayList<Deferred<Any?>>()

    override fun begin() {
        runBlocking {
            sendTasks.forEach { it.await() }
            sendTasks.clear()
        }
    }

    override fun process(entityId: Int) {
        val client = clientComponentMapper[entityId] ?: return

        val events = client.getEvents()

        val tcpArray = events.filter { it.sendType == SendType.TCP }.map { it.data }.toTypedArray()
        val udpArray = events.filter { it.sendType == SendType.UDP }.map { it.data }.toTypedArray()

        if (tcpArray.isNotEmpty()) {
            client.sendPacket(SendContainer(GamePacket(tcpArray), SendType.TCP))
        }
        if (udpArray.isNotEmpty()) {
            client.sendPacket(SendContainer(GamePacket(udpArray), SendType.UDP))
        }

        client.clearEvents()
    }

    private fun ClientComponent.sendPacket(packet: SendContainer<GamePacket>) {
        val deferred = scope.async {
            try {
                when (packet.sendType) {
                    SendType.TCP -> this@sendPacket.connection?.sendTCP(packet.data)
                    SendType.UDP -> this@sendPacket.connection?.sendUDP(packet.data)
                }
            } catch (_: Throwable) {
            }
        }
        sendTasks.add(deferred)
    }

    override fun dispose() {
        scope.cancel()
    }
}
