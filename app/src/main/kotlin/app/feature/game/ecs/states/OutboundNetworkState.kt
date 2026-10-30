package app.feature.game.ecs.states

import com.gigcreator.NetworkEvent
import com.gigcreator.network.SendContainer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class OutboundNetworkState {

    private val tcpEvents = ConcurrentLinkedQueue<NetworkEvent>()
    private val udpEvents = ConcurrentLinkedQueue<NetworkEvent>()

    fun addTCP(event: NetworkEvent) {
        tcpEvents.add(event)
    }

    fun addUDP(event: NetworkEvent) {
        udpEvents.add(event)
    }

    /** Атомарно забирает все TCP-события; новые события после начала drain не теряются. */
    fun drainTCP(): Array<NetworkEvent> {
        val result = ArrayList<NetworkEvent>()
        while (true) {
            val event = tcpEvents.poll() ?: break
            result += event
        }
        return result.toTypedArray()
    }

    fun replaceUDP(): Array<NetworkEvent> {
        try {
            return udpEvents.toTypedArray()
        } finally {
            udpEvents.clear()
        }
    }

    fun clear() {
        tcpEvents.clear()
        udpEvents.clear()
    }
}