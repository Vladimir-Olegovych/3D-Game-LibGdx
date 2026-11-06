package app.feature.main

import app.feature.dialogs.SettingsDialog
import app.feature.dialogs.SettingsDialogCallbacks
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.gigapi.fillDraw
import com.gigapi.fragment.Fragment
import com.gigapi.general.GContext
import com.gigapi.setOnClickListener
import com.gigapi.storage.json.AppConfig
import com.gigapi.viewport.UnfairViewport
import core.assets.SkinID
import core.configs.ConfigTypes
import core.defaults.CameraTypes
import core.navigation.Navigation
import core.settings.SettingsSO
import core.viewport.ViewportTypes

class MainFragment(
    private val navigation: Navigation.Main,
    private val gContext: GContext,
    private val onWorldSelectScreen: () -> Unit,
    private val onMultiplayerScreen: () -> Unit
): Fragment() {

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var stage: Stage
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: UnfairViewport
    private lateinit var menuBackground: Texture
    private lateinit var settingsDialog: SettingsDialog

    override fun onCreate() {
        spriteBatch = gContext.getObject()
        viewport = gContext.getObject(ViewportTypes.UNFAIR)
        stage = gContext.getObject()
        camera = gContext.getObject(CameraTypes.GL_2D)
        val camera3d = gContext.getObject<PerspectiveCamera>(CameraTypes.GL_3D)
        val assetManager = gContext.getObject<AssetManager>()
        menuBackground = assetManager.get<Skin>(SkinID.BLOCK.skin).atlas.textures.first()
        settingsDialog = SettingsDialog(
            camera = camera3d,
            settingsSO = gContext.getObject<AppConfig<SettingsSO>>(ConfigTypes.SETTINGS_SO).getConfig(),
            assetManager = assetManager,
            viewport = viewport,
            callbacks = SettingsDialogCallbacks(
                onBack = {
                    Gdx.input.inputProcessor = stage
                }
            )
        )

        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        val menuTable = Table().apply {
            setFillParent(true)
            top()
        }

        val playButton = TextButton("Play", skin).setOnClickListener {
            onWorldSelectScreen.invoke()
        }
        val multiplayerButton = TextButton("Multiplayer", skin).setOnClickListener {
            onMultiplayerScreen.invoke()
        }
        val settingsButton = TextButton("Settings", skin).setOnClickListener {
            settingsDialog.show(dialogManager)
        }
        val quitButton = TextButton("Quit", skin).setOnClickListener {
            Gdx.app.exit()
        }

        menuTable.add(playButton).height(40F).width(200F).padTop(8F).row()
        menuTable.add(multiplayerButton).height(40F).width(200F).padTop(8F).row()
        menuTable.add(settingsButton).height(40F).width(200F).padTop(8F).row()
        menuTable.add(quitButton).height(40F).width(200F).padTop(8F).row()

        stage.addActor(menuTable)
        Gdx.input.inputProcessor = stage
    }

    override fun onRender(deltaTime: Float) {
        if (settingsDialog.isShowed()) return
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
        settingsDialog.dismiss()
        stage.clear()
    }
}