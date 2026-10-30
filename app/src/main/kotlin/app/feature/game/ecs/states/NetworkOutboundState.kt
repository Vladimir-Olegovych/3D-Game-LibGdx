package app.feature.game.ecs.states

data class OutboundEntityState(
    val entityId: Int,
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

    @Volatile
    private var states: List<OutboundEntityState> = emptyList()

    fun replace(newStates: List<OutboundEntityState>) {
        states = newStates
    }

    fun snapshot(): List<OutboundEntityState> = states

    fun clear() {
        states = emptyList()
    }
}
