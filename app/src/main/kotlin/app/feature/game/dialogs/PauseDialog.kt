package app.feature.game.dialogs

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.gigapi.dialogs.Dialog
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.setOnClickListener
import com.gigapi.texture.ColorDrawable
import core.assets.SkinID
import core.controls.UiInputProcessor

class PauseDialog: LaunchedEffect, Dialog() {

    enum class State {
        RESUME,
        SETTINGS,
        QUIT
    }

    private lateinit var stage: Stage
    private lateinit var fullscreenOverlay: Table

    override fun launch(context: GContext) {
        stage = context.getObject()
        val uiInputProcessor = context.getObject<UiInputProcessor>()
        val assetManager = context.getObject<AssetManager>()
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        fullscreenOverlay = Table().apply {
            setFillParent(true)
            background(ColorDrawable(0f, 0f, 0f, 0.7f))
            add(
                TextButton("resume", skin).setOnClickListener {
                    uiInputProcessor.onPauseDialog(State.RESUME)
                }
            ).height(40F).width(70F).row()
            add(
                TextButton("settings", skin).setOnClickListener {
                    uiInputProcessor.onPauseDialog(State.SETTINGS)
                }
            ).height(40F).width(70F).padTop(8F).row()
            add(
                TextButton("quit", skin).setOnClickListener {
                    uiInputProcessor.onPauseDialog(State.QUIT)
                }
            ).height(40F).width(70F).padTop(8F).row()
        }
    }

    override fun onCreate() {
        stage.addActor(fullscreenOverlay)
    }

    override fun onDestroy() {
        fullscreenOverlay.remove()
    }

}