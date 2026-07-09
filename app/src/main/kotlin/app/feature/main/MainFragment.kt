package app.feature.main

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.gigapi.fillDraw
import com.gigapi.fragment.Fragment
import com.gigapi.general.Context
import com.gigapi.setOnClickListener
import com.gigapi.viewport.UnfairViewport
import core.assets.SkinID
import core.defaults.CameraTypes
import core.navigation.Navigation
import core.viewport.ViewportTypes

class MainFragment(
    private val navigation: Navigation.Main,
    private val context: Context,
    private val onGameScreen: () -> Unit
): Fragment() {

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var stage: Stage
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: UnfairViewport
    private lateinit var menuBackground: Texture

    override fun onCreate() {
        spriteBatch = context.getObject()
        viewport = context.getObject(ViewportTypes.UNFAIR)
        stage = context.getObject()
        camera = context.getObject(CameraTypes.GL_2D)
        val assetManager = context.getObject<AssetManager>()
        menuBackground = assetManager.get<Skin>(SkinID.BLOCK.skin).atlas.textures.first()

        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        val menuTable = Table().apply {
            setFillParent(true)
            top()
        }

        val playButton = TextButton("play", skin).setOnClickListener {
            onGameScreen.invoke()
        }
        val settingsButton = TextButton("settings", skin).setOnClickListener {
            println("settingsButton")
        }
        val quitButton = TextButton("quit", skin).setOnClickListener {
            Gdx.app.exit()
        }

        menuTable.add(playButton).height(40F).width(200F).padTop(8F).row()
        menuTable.add(settingsButton).height(40F).width(200F).padTop(8F).row()
        menuTable.add(quitButton).height(40F).width(200F).padTop(8F).row()

        stage.addActor(menuTable)
        Gdx.input.inputProcessor = stage
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

    override fun onResume() {

    }

    override fun onPause() {

    }

    override fun onDestroy() {
        Gdx.input.inputProcessor = null
        stage.clear()
    }
}