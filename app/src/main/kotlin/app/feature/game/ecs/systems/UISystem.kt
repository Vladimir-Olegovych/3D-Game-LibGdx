package app.feature.game.ecs.systems

import app.feature.game.dialogs.PauseDialog
import app.feature.game.event.UiEvent
import com.artemis.BaseSystem
import com.artemis.annotations.Wire
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Stage
import com.gigapi.dialogs.DialogManager
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.viewport.UnfairViewport
import core.controls.PlayerInputProcessor
import core.controls.UiInputProcessor
import core.viewport.ViewportTypes

class UISystem: BaseSystem() {

    @Wire
    private lateinit var stage: Stage
    @Wire
    private lateinit var assetManager: AssetManager
    @Wire(name = ViewportTypes.UNFAIR)
    private lateinit var viewport: UnfairViewport

    @Wire
    private lateinit var dialogManager: DialogManager
    @Wire
    private lateinit var pauseDialog: PauseDialog

    @Wire
    private lateinit var inputMultiplexer: InputMultiplexer
    @Wire
    private lateinit var playerInputProcessor: PlayerInputProcessor
    @Wire
    private lateinit var uiInputProcessor: UiInputProcessor

    override fun initialize() {
        /*
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        val uiTable = Table().apply {
            setFillParent(true)
            top()
        }
        val quitButton = TextButton("quit", skin).setOnClickListener {
            Gdx.app.exit()
        }
        uiTable.add(quitButton).height(40F).width(200F).padTop(8F)
        stage.addActor(uiTable)
         */
    }

    @BusEvent
    fun onPauseOpen(event: UiEvent.OnPauseOpen) {
        playerInputProcessor.clear()
        inputMultiplexer.removeProcessor(playerInputProcessor)
        Gdx.input.isCursorCatched = false
        if (!pauseDialog.isShowed()) pauseDialog.show(dialogManager)
    }

    @BusEvent
    fun onPauseClose(event: UiEvent.OnPauseClose) {
        inputMultiplexer.addProcessor(playerInputProcessor)
        Gdx.input.isCursorCatched = true
        if (pauseDialog.isShowed()) pauseDialog.dismiss()
    }

    @BusEvent
    fun onInventoryOpen(event: UiEvent.OnInventoryOpen) {
        playerInputProcessor.clear()
        inputMultiplexer.removeProcessor(playerInputProcessor)
        Gdx.input.isCursorCatched = false
    }

    @BusEvent
    fun onInventoryClose(event: UiEvent.OnInventoryClose) {
        inputMultiplexer.addProcessor(playerInputProcessor)
        Gdx.input.isCursorCatched = true
    }

    override fun processSystem() {
        viewport.apply()
        stage.act(world.delta)
        stage.draw()
    }

    override fun dispose() {
        stage.clear()
    }
}