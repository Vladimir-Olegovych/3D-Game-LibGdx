package app.feature.game.ui

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.Context
import core.assets.SkinID
import core.ui.UIGetter

class FpsUI : LaunchedEffect, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        right()
        top()
    }
    private lateinit var fpsLabel: Label

    override fun launch(context: Context) {
        val assetManager = context.getObject<AssetManager>()
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        fpsLabel = Label("FPS: Not updated", skin)
        layout.add(fpsLabel)
    }

    private var frameCount = 0
    private var elapsedTime = 0f
    private var currentFps = 0

    fun update(deltaTime: Float) {
        frameCount++
        elapsedTime += deltaTime

        if (elapsedTime >= 1f) {
            currentFps = frameCount
            frameCount = 0
            elapsedTime = 0f

            if (currentFps < 60) {
                fpsLabel.setColor(1f, 0f, 0f, 1f)
            } else {
                fpsLabel.setColor(1f, 1f, 1f, 1f)
            }

            fpsLabel.setText("FPS: $currentFps")
        }
    }
    override fun getUI(): Actor = layout
}