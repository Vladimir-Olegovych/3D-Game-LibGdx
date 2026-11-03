package core.navigation

import core.chunk.kryo.WorldData
import core.client.GameServerAddress

sealed class Navigation {
    data object Main: Navigation()
    data object Multiplayer: Navigation()
    data object WorldSelect: Navigation()
    data class Game(
        val isLocal: Boolean,
        val worldData: WorldData?,
        val joinParams: GameServerAddress = GameServerAddress(
            address = "127.0.0.1",
            port = 5551
        )
    ) : Navigation()
}