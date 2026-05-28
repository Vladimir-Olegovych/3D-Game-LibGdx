package core.defaults

import com.artemis.World

object WorldConstants {

    private val playerIds = ArrayList<Int>()

    fun initialize(world: World) {
        val playerId = world.create()
        playerIds.add(playerId)
    }

    fun getPlayerEntityId(): Int = playerIds[0]
}