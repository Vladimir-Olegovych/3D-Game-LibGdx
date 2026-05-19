package app

import app.feature.game.GameFragment
import app.feature.main.MainFragment
import com.badlogic.gdx.Game
import com.gigapi.general.Context
import com.gigapi.screens.navigation.NavHostController
import core.assets.AssetsSetupManager
import core.configs.ConfigSetupManager
import core.defaults.DefaultGameSetupManager
import core.navigation.Navigation

class GameApplication :  Game() {

    private val context = Context()

    override fun dispose() { context.dispose() }

    override fun create() {
        val navHostController = NavHostController<Navigation>(this)
        AssetsSetupManager.launch(context)
        ConfigSetupManager.launch(context)
        DefaultGameSetupManager.launch(context)
        context.setObject(navHostController)
        context.launch()

        navHostController.apply {
            fragment<Navigation.Main> {
                return@fragment MainFragment(
                    navigation = it,
                    context = context,
                    onGameScreen = {
                        navHostController.navigate(Navigation.Game())
                    },
                )
            }
            fragment<Navigation.Game> {
                return@fragment GameFragment(
                    navigation = it,
                    context = context
                )
            }
            navigate(Navigation.Main)
        }
    }

}