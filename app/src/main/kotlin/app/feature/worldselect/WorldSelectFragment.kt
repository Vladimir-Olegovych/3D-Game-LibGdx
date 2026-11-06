package app.feature.worldselect

import app.feature.main.dialogs.CreateWorldCallbacks
import app.feature.main.dialogs.CreateWorldDialog
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
import com.gigapi.texture.ColorDrawable
import com.gigapi.viewport.UnfairViewport
import core.assets.SkinID
import core.chunk.kryo.MetaData
import core.chunk.kryo.createWorldData
import core.chunk.kryo.readMetaData
import core.chunk.kryo.readWorldData
import core.defaults.CameraTypes
import core.defaults.WorldConstants
import core.navigation.Navigation
import core.utils.safeName
import core.viewport.ViewportTypes
import java.io.File
import kotlin.random.Random

class WorldSelectFragment(
    private val navigation: Navigation.WorldSelect,
    private val gContext: GContext,
    private val onGameScreen: (Navigation.Game) -> Unit,
    private val onMenuScreen: () -> Unit
): Fragment() {

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var stage: Stage
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: UnfairViewport
    private lateinit var menuBackground: Texture
    private lateinit var buttonSkin: Skin
    private lateinit var worldsTable: Table
    private lateinit var createWorldDialog: CreateWorldDialog

    private val worlds = ArrayList<Pair<File, MetaData>>()
    private var selectedIndex = -1

    override fun onCreate() {
        spriteBatch = gContext.getObject()
        viewport = gContext.getObject(ViewportTypes.UNFAIR)
        stage = gContext.getObject()
        camera = gContext.getObject(CameraTypes.GL_2D)
        val assetManager = gContext.getObject<AssetManager>()
        menuBackground = assetManager.get<Skin>(SkinID.BLOCK.skin).atlas.textures.first()
        buttonSkin = assetManager.get(SkinID.BUTTON.skin)

        worlds.clear()
        val savesDirectory = File(WorldConstants.SAVES_DIRECTORY)
        if (!savesDirectory.exists()) savesDirectory.mkdir()

        val listFiles = savesDirectory.listFiles()
        if (listFiles != null) for (file in listFiles) {
            if (!file.isFile) continue
            runCatching { readMetaData(file) }.onFailure {
                it.printStackTrace()
            }.onSuccess { metaData ->
                worlds.add(Pair(file, metaData))
            }
        }

        createWorldDialog = CreateWorldDialog(
            gContext = gContext,
            callbacks = CreateWorldCallbacks(
                onConfirm = { name, seed ->
                    val safeWorldName = name.safeName()
                    val worldToCreate = File(WorldConstants.SAVES_DIRECTORY + "$safeWorldName.world")
                    val worldData = runCatching {
                        createWorldData(worldToCreate, name, (seed?: Random.nextInt()).toLong())
                    }.getOrNull()


                    onGameScreen.invoke(Navigation.Game(
                        isLocal = true,
                        worldData = worldData,
                    ))
                },
                onCancel = {

                }
            )
        )

        worldsTable = Table().apply { top() }
        val scrollPane = ScrollPane(worldsTable).apply {
            setScrollingDisabled(true, false)
            setFadeScrollBars(false)
        }

        val createButton = TextButton("create world", buttonSkin).setOnClickListener {
            createWorldDialog.show(dialogManager)
        }
        val loadButton = TextButton("load selected", buttonSkin).setOnClickListener {
            val data = worlds.getOrNull(selectedIndex) ?: return@setOnClickListener
            onGameScreen.invoke(Navigation.Game(
                isLocal = true,
                worldData = runCatching { readWorldData(data.first) }.getOrNull()
            ))
        }
        val backButton = TextButton("back", buttonSkin).setOnClickListener {
            onMenuScreen.invoke()
        }

        val root = Table().apply {
            setFillParent(true)
            top()
            add(Label("Select world", buttonSkin, "small")).padTop(12f).padBottom(8f).row()
            add(scrollPane).width(300f).height(150f).row()
            add(loadButton).height(40F).width(300F).padTop(8F).row()
            add(createButton).height(40F).width(300F).padTop(8F).row()
            add(backButton).height(40F).width(300F).padTop(8F).row()
        }

        refreshWorlds()
        stage.addActor(root)
        Gdx.input.inputProcessor = stage
    }

    private fun refreshWorlds() {
        worldsTable.clearChildren()
        worlds.forEachIndexed { index, data ->
            val row = Table().apply {
                touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
                pad(6f)
                background(
                    if (index == selectedIndex) ColorDrawable(0.2f, 0.5f, 0.9f, 0.8f)
                    else ColorDrawable(0f, 0f, 0f, 0.4f)
                )
                add(Label(data.second.name, buttonSkin, "small")).left().expandX()
            }
            row.setOnClickListener {
                selectedIndex = index
                refreshWorlds()
            }
            worldsTable.add(row).width(280f).padBottom(4f).row()
        }
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
        createWorldDialog.dismiss()
        Gdx.input.inputProcessor = null
        stage.clear()
    }
}