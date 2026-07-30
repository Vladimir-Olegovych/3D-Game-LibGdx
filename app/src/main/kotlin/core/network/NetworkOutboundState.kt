package core.network

import java.util.concurrent.ConcurrentHashMap

data class OutboundEntityState(
    val entityId: Int,
    val entityType: Byte,
    val modelId: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float,
)

class NetworkOutboundState {
    private val entities = ConcurrentHashMap<Int, OutboundEntityState>()

    fun put(state: OutboundEntityState) {
        entities[state.entityId] = state
    }

    fun remove(entityId: Int) {
        entities.remove(entityId)
    }

    fun clear() {
        entities.clear()
    }

    fun snapshot(): List<OutboundEntityState> = entities.values.toList()
}
