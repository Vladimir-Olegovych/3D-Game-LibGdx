package app.feature.game.dialogs

import app.feature.dialogs.SettingsDialog
import app.feature.dialogs.SettingsDialogCallbacks
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.gigapi.dialogs.Dialog
import com.gigapi.setOnClickListener
import com.gigapi.storage.json.AppConfig
import com.gigapi.texture.ColorDrawable
import com.gigapi.viewport.UnfairViewport
import core.assets.SkinID
import core.client.GameServerAddress
import core.navigation.Navigation
import core.settings.SettingsSO

class PauseDialogCallbacks(
    val onDismiss: () -> Unit,
    val onResume: () -> Unit,
    val onQuit: () -> Unit,
    val openLanWorld: (GameServerAddress) -> Boolean
)

class PauseDialog(
    private val stage: Stage,
    private val viewport: UnfairViewport,
    private val settingsSO: SettingsSO,
    private val navigation: Navigation.Game,
    private val assetManager: AssetManager,
    private val inputMultiplexer: InputMultiplexer,
    private val callbacks: PauseDialogCallbacks
) : Dialog() {

    private val fullscreenOverlay: Table

    init {
        val settingsCallbacks = SettingsDialogCallbacks(
            onBack = {
                Gdx.input.inputProcessor = inputMultiplexer
            }
        )

        val settingsDialog = SettingsDialog(
            settingsSO = settingsSO,
            assetManager = assetManager,
            viewport = viewport,
            callbacks = settingsCallbacks
        )
        val buttonSkin = assetManager.get<Skin>(SkinID.BUTTON.skin)

        val portField = TextField("5551", buttonSkin).apply {
            messageText = "Server port"
        }

        val openLanButton = TextButton("Open LAN", buttonSkin)
        openLanButton.setOnClickListener {
            val portText = portField.text.trim()
            val port = portText.toIntOrNull()

            if (port == null || port !in 1..65535) {
                portField.messageText = "Invalid port"
                return@setOnClickListener
            }

            dismiss()
            callbacks.onDismiss()
            val isOpened = callbacks.openLanWorld.invoke(GameServerAddress(address = "127.0.0.1", port = port))
            if (isOpened) {
                portField.remove()
                openLanButton.remove()
            }
        }

        fullscreenOverlay = Table().apply {
            setFillParent(true)
            background(ColorDrawable(0f, 0f, 0f, 0.7f))

            add(
                TextButton("resume", buttonSkin).setOnClickListener {
                    dismiss()
                    callbacks.onDismiss()
                    callbacks.onResume()
                }
            ).height(40F).width(70F).row()

            add(
                TextButton("settings", buttonSkin).setOnClickListener {
                    dialogManager?.let { settingsDialog.show(it) }
                }
            ).height(40F).width(70F).padTop(8F).row()

            add(
                TextButton("quit", buttonSkin).setOnClickListener {
                    dismiss()
                    callbacks.onDismiss()
                    callbacks.onQuit()
                }
            ).height(40F).width(70F).padTop(8F).row()

            if (navigation.isLocal) {
                add(portField)
                    .height(32F)
                    .width(140F)
                    .padTop(8F)
                    .row()

                add(openLanButton)
                    .height(40F)
                    .width(140F)
                    .padTop(8F)
                    .row()
            }
        }
    }

    override fun onShow() {
        stage.addActor(fullscreenOverlay)
    }

    override fun onHide() {
        fullscreenOverlay.remove()
    }
}