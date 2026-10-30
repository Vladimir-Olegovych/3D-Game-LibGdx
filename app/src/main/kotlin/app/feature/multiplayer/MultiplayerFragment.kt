package app.feature.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.gigapi.fillDraw
import com.gigapi.fragment.Fragment
import com.gigapi.general.GContext
import com.gigapi.setOnClickListener
import com.gigapi.viewport.UnfairViewport
import core.assets.SkinID
import core.client.GameServerAddress
import core.defaults.CameraTypes
import core.navigation.Navigation
import core.viewport.ViewportTypes

class MultiplayerFragment(
    private val navigation: Navigation.Multiplayer,
    private val gContext: GContext,
    private val onGameScreen: (Navigation.Game) -> Unit,
    private val onMenuScreen: () -> Unit
) : Fragment() {

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var stage: Stage
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: UnfairViewport
    private lateinit var menuBackground: Texture
    private lateinit var buttonSkin: Skin

    private lateinit var addressField: TextField
    private lateinit var portField: TextField
    private lateinit var errorLabel: Label

    override fun onCreate() {
        spriteBatch = gContext.getObject()
        viewport = gContext.getObject(ViewportTypes.UNFAIR)
        stage = gContext.getObject()
        camera = gContext.getObject(CameraTypes.GL_2D)

        val assetManager = gContext.getObject<AssetManager>()
        menuBackground = assetManager.get<Skin>(SkinID.BLOCK.skin).atlas.textures.first()
        buttonSkin = assetManager.get(SkinID.BUTTON.skin)

        addressField = TextField("127.0.0.1", buttonSkin).apply {
            messageText = "Server address"
        }

        portField = TextField("5551", buttonSkin).apply {
            messageText = "Server port"
        }

        errorLabel = Label("", buttonSkin, "small").apply {
            isVisible = false
        }

        val connectButton = TextButton("Connect", buttonSkin).setOnClickListener {
            connectToServer()
        }

        val backButton = TextButton("back", buttonSkin).setOnClickListener {
            onMenuScreen.invoke()
        }

        val fields = Table().apply {
            add(Label("Address", buttonSkin, "small")).left().padRight(8f)
            add(addressField).width(190f).height(32f).row()

            add(Label("Port", buttonSkin, "small")).left().padRight(8f).padTop(8f)
            add(portField).width(190f).height(32f).padTop(8f).row()

            add(errorLabel).colspan(2).padTop(6f).row()
        }

        val root = Table().apply {
            setFillParent(true)
            top()
            add(Label("Multiplayer", buttonSkin, "small")).padTop(12f).padBottom(12f).row()
            add(fields).padBottom(16f).row()
            add(connectButton).height(40f).width(300f).padBottom(8f).row()
            add(backButton).height(40f).width(300f).row()
        }

        stage.addActor(root)
        Gdx.input.inputProcessor = stage
    }

    private fun connectToServer() {
        val address = addressField.text.trim()
        val port = portField.text.trim().toIntOrNull()

        if (address.isEmpty()) {
            showError("Enter server address")
            return
        }

        if (port == null || port !in 1..65535) {
            showError("Invalid port")
            return
        }

        hideError()

        onGameScreen.invoke(
            Navigation.Game(
                isLocal = false,
                worldName = "",
                joinParams = GameServerAddress(
                    address = address,
                    port = port
                )
            )
        )
    }

    private fun showError(message: String) {
        errorLabel.setText(message)
        errorLabel.isVisible = true
    }

    private fun hideError() {
        errorLabel.isVisible = false
    }

    override fun onRender(deltaTime: Float) {
        Gdx.gl.glClearColor(135 / 255f, 206 / 255f, 235 / 255f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        stage.act(deltaTime)
        viewport.apply()

        spriteBatch.projectionMatrix = camera.combined
        spriteBatch.begin()
        spriteBatch.fillDraw(menuBackground, camera)
        spriteBatch.end()

        stage.draw()
    }

    override fun onResize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun onResume() {}
    override fun onPause() {}

    override fun onDestroy() {
        Gdx.input.inputProcessor = null
        stage.clear()
    }
}