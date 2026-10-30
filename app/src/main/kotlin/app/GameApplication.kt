package app

import app.feature.game.GameFragment
import app.feature.main.MainFragment
import app.feature.multiplayer.MultiplayerFragment
import app.feature.worldselect.WorldSelectFragment
import com.badlogic.gdx.Game
import com.gigapi.general.GContext
import com.gigapi.navigation.NavHostController
import core.assets.AssetsSetupManager
import core.configs.ConfigSetupManager
import core.defaults.DefaultGameSetupManager
import core.navigation.Navigation

class GameApplication :  Game() {

    private val gContext = GContext()

    override fun dispose() { gContext.dispose() }

    override fun create() {
        val navHostController = NavHostController<Navigation>(this)
        AssetsSetupManager.launch(gContext)
        ConfigSetupManager.launch(gContext)
        DefaultGameSetupManager.launch(gContext)
        gContext.setObject(navHostController)
        gContext.launch()
        navHostController.apply {
            fragment<Navigation.Main> {
                return@fragment MainFragment(
                    navigation = it,
                    gContext = gContext,
                    onWorldSelectScreen = {
                        navHostController.navigate(Navigation.WorldSelect)
                    },
                    onMultiplayerScreen = {
                        navHostController.navigate(Navigation.Multiplayer)
                    }
                )
            }
            fragment<Navigation.WorldSelect> {
                return@fragment WorldSelectFragment(
                    navigation = it,
                    gContext = gContext,
                    onGameScreen = { navigation ->
                        navHostController.navigate(navigation)
                    },
                    onMenuScreen = {
                        navHostController.navigate(Navigation.Main)
                    }
                )
            }
            fragment<Navigation.Multiplayer> {
                return@fragment MultiplayerFragment(
                    navigation = it,
                    gContext = gContext,
                    onGameScreen = { navigation ->
                        navHostController.navigate(navigation)
                    },
                    onMenuScreen = {
                        navHostController.navigate(Navigation.Main)
                    }
                )
            }
            fragment<Navigation.Game> {
                return@fragment GameFragment(
                    navigation = it,
                    gContext = gContext,
                    onMenuScreen = {
                        navHostController.navigate(Navigation.Main)
                    }
                )
            }
            navigate(Navigation.Main)
        }
    }

}