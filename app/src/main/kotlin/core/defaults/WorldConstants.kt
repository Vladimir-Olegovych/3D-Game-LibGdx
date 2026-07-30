package core.defaults

import com.artemis.World

object WorldConstants {

    private var localPlayerEntityId = -1

    fun initializeLocalPlayer(world: World) {
        localPlayerEntityId = world.create()
    }

    fun getLocalPlayerEntityId(): Int = localPlayerEntityId
}
