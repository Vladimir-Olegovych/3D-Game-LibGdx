package app.feature.game.dialogs

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.gigapi.dialogs.Dialog
import com.gigapi.setOnClickListener
import com.gigapi.texture.ColorDrawable
import core.assets.SkinID

class PauseDialogCallbacks(
    val onResume: () -> Unit,
    val onSettings: () -> Unit,
    val onQuit: () -> Unit
)

class PauseDialog(
    private val stage: Stage,
    private val assetManager: AssetManager,
    private val callbacks: PauseDialogCallbacks
): Dialog() {

    private val fullscreenOverlay: Table

    init {
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        fullscreenOverlay = Table().apply {
            setFillParent(true)
            background(ColorDrawable(0f, 0f, 0f, 0.7f))
            add(
                TextButton("resume", skin).setOnClickListener {
                    dismiss()
                    callbacks.onResume()
                }
            ).height(40F).width(70F).row()
            add(
                TextButton("settings", skin).setOnClickListener {
                    dismiss()
                    callbacks.onSettings()
                }
            ).height(40F).width(70F).padTop(8F).row()
            add(
                TextButton("quit", skin).setOnClickListener {
                    dismiss()
                    callbacks.onQuit()
                }
            ).height(40F).width(70F).padTop(8F).row()
        }
    }


    override fun onShow() {
        stage.addActor(fullscreenOverlay)
    }

    override fun onHide() {
        fullscreenOverlay.remove()
    }

}