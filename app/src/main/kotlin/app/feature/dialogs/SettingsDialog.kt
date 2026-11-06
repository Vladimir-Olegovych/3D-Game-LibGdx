package app.feature.dialogs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.gigapi.dialogs.Dialog
import com.gigapi.setOnClickListener
import com.gigapi.texture.ColorDrawable
import com.gigapi.viewport.UnfairViewport
import core.assets.SkinID
import core.settings.SettingsSO

class SettingsDialogCallbacks(
    val onBack: () -> Unit
)

class SettingsDialog(
    private val settingsSO: SettingsSO,
    private val assetManager: AssetManager,
    private val viewport: UnfairViewport,
    private val callbacks: SettingsDialogCallbacks
) : Dialog() {


    private val stage = Stage(viewport)

    private val fullscreenOverlay = Table().apply {
        touchable = Touchable.enabled
        setFillParent(true)
        center()
    }

    private var lodEnabledButton: TextButton
    private var lodDrawRadiusField: TextField
    private var drawRadiusXzField: TextField
    private var drawRadiusYField: TextField

    init {
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)


        lodEnabledButton = TextButton("LOD", skin).apply {
            val onSwitchStatus = {
                val text = if (settingsSO.isLodEnabled) "LOD Enabled" else "LOD Disabled"
                this@apply.setText(text)
            }

            onSwitchStatus.invoke()
            setOnClickListener {
                settingsSO.isLodEnabled = !settingsSO.isLodEnabled
                onSwitchStatus.invoke()
            }
        }

        lodDrawRadiusField = floatField(settingsSO.lodDrawRadius.toString(), skin)
        drawRadiusXzField = intField(settingsSO.drawRadiusXZ.toString(), skin)
        drawRadiusYField = intField(settingsSO.drawRadiusY.toString(), skin)

        val backButton = TextButton("Back", skin).setOnClickListener {
            dismiss()
            saveSettings()
            callbacks.onBack.invoke()
        }

        fullscreenOverlay.apply {
            background(ColorDrawable(0.15f, 0.15f, 0.15f, 0.95f))
            add(Label("Amogus Craft Settings", skin)).padBottom(20f).row()

            addSettingRow(this, skin, "LOD Program", lodEnabledButton)
            addSettingRow(this, skin, "LOD draw radius", lodDrawRadiusField)
            addSettingRow(this, skin, "Draw radius XZ", drawRadiusXzField)
            addSettingRow(this, skin, "Draw radius Y", drawRadiusYField)

            add(backButton)
                .height(40f)
                .width(200f)
                .row()
        }
    }

    private fun addSettingRow(
        table: Table,
        skin: Skin,
        title: String,
        field: Actor
    ) {
        table.add(Label(title, skin)).left().padRight(10f)
        table.add(field).width(160f).height(40f).padBottom(10f).row()
    }

    private fun floatField(text: String, skin: Skin): TextField =
        TextField(text, skin).apply {
            messageText = "Float"
            setTextFieldFilter { _, c ->
                c.isDigit() || c == '.' || c == '-'
            }
        }

    private fun intField(text: String, skin: Skin): TextField =
        TextField(text, skin).apply {
            messageText = "Integer"
            setTextFieldFilter { _, c -> c.isDigit() || c == '-' }
        }

    private fun saveSettings() {
        settingsSO.isLodEnabled = lodEnabledButton.isChecked
        settingsSO.lodDrawRadius = lodDrawRadiusField.text.toFloatOrNull()
            ?: settingsSO.lodDrawRadius
        settingsSO.drawRadiusXZ = drawRadiusXzField.text.toIntOrNull()
            ?: settingsSO.drawRadiusXZ
        settingsSO.drawRadiusY = drawRadiusYField.text.toIntOrNull()
            ?: settingsSO.drawRadiusY
    }

    override fun onShow() {
        Gdx.input.inputProcessor = stage
        stage.addActor(fullscreenOverlay)
    }

    override fun onHide() {
        Gdx.input.inputProcessor = null
        fullscreenOverlay.remove()
    }

    override fun onRender(deltaTime: Float) {
        Gdx.gl.glClearColor(135 / 255f, 206 / 255f, 235 / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        stage.act(deltaTime)
        stage.draw()
    }
}