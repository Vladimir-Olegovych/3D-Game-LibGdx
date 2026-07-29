package app

import app.feature.game.GameFragment
import app.feature.main.MainFragment
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
                    onGameScreen = {
                        navHostController.navigate(Navigation.Game())
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