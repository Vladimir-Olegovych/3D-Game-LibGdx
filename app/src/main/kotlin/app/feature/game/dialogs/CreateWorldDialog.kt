package app.feature.game.dialogs

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.gigapi.general.GContext
import com.gigapi.setOnClickListener
import com.gigapi.texture.ColorDrawable
import core.assets.SkinID

class CreateWorldCallbacks(
    val onConfirm: (worldName: String, seed: Int?) -> Unit,
    val onCancel: () -> Unit = {}
)

class CreateWorldDialog(
    private val gContext: GContext,
    private val callbacks: CreateWorldCallbacks
) {
    private val stage: Stage = gContext.getObject()
    private val buttonSkin: Skin = gContext.getObject<AssetManager>().get(SkinID.BUTTON.skin)

    private var overlay: Table? = null

    fun show() {
        if (overlay != null) return

        val nameField = TextField("", buttonSkin).apply { messageText = "World name" }
        val seedField = TextField("", buttonSkin).apply { messageText = "Seed (optional)" }
        val errorLabel = Label("", buttonSkin, "small").apply { isVisible = false }

        val createButton = TextButton("create", buttonSkin).setOnClickListener {
            val name = nameField.text.trim()
            if (name.isEmpty()) {
                errorLabel.setText("Enter world name")
                errorLabel.isVisible = true
                return@setOnClickListener
            }
            val seed = parseSeed(seedField.text)
            hide()
            callbacks.onConfirm(name, seed)
        }
        val cancelButton = TextButton("cancel", buttonSkin).setOnClickListener {
            hide()
            callbacks.onCancel()
        }

        val content = Table().apply {
            background(ColorDrawable(0.15f, 0.15f, 0.15f, 0.95f))
            pad(16f)
            add(Label("Create world", buttonSkin, "small")).colspan(2).padBottom(10f).row()
            add(nameField).colspan(2).width(260f).height(32f).padBottom(8f).row()
            add(seedField).colspan(2).width(260f).height(32f).padBottom(8f).row()
            add(errorLabel).colspan(2).padBottom(8f).row()
            add(createButton).width(124f).height(36f).padRight(12f)
            add(cancelButton).width(124f).height(36f)
        }

        overlay = Table().apply {
            setFillParent(true)
            touchable = Touchable.enabled // блокирует клики по меню под диалогом
            background(ColorDrawable(0f, 0f, 0f, 0.6f))
            add(content).center()
        }
        stage.addActor(overlay)
        stage.keyboardFocus = nameField
    }

    fun hide() {
        overlay?.remove()
        overlay = null
        stage.keyboardFocus = null
    }

    private fun parseSeed(text: String): Int? {
        val s = text.trim()
        if (s.isEmpty()) return null
        return s.toIntOrNull()
    }
}