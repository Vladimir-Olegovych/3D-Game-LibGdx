package core.navigation

sealed class Navigation {
    data object Main: Navigation()
    data object WorldSelect: Navigation()
    data class Game(
        val isLocal: Boolean,
        val worldName: String,
        val seed: Int? = null,
        val address: String = "127.0.0.1",
        val port: Int = 5000
    ) : Navigation()
}