package core.navigation

import core.client.GameServerAddress

sealed class Navigation {
    data object Main: Navigation()
    data object Multiplayer: Navigation()
    data object WorldSelect: Navigation()
    data class Game(
        val isLocal: Boolean,
        val worldName: String,
        val seed: Int? = null,
        val joinParams: GameServerAddress = GameServerAddress(
            address = "127.0.0.1",
            port = 5551
        )
    ) : Navigation()
}