package core.navigation

sealed class Navigation {
    data object Main: Navigation()
    data class Game(val address: String = "127.0.0.1", val port: Int = 5000) : Navigation()
}