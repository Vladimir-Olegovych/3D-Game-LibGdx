package com.gigcreator

import app.GameApplication
import com.badlogic.gdx.Game
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import kotlin.math.roundToInt

fun main() {
    val gameApplication = GameApplication()
    startScreen(gameApplication)
}

private fun startScreen(game: Game){
    val config = Lwjgl3ApplicationConfiguration()
    config.useVsync(false)
    config.setForegroundFPS((Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate * 2))
    config.setIdleFPS(30)
    config.setTitle("Amogus Craft")
    //config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
    Lwjgl3Application(game, config)
}