package app.feature.game.ecs.systems

import app.feature.game.dialogs.InventoryDialog
import app.feature.game.dialogs.PauseDialog
import app.feature.game.event.UiEvent
import app.feature.game.ui.AimUI
import app.feature.game.ui.DataUI
import app.feature.game.ui.InventoryUI
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
    private lateinit var inventoryDialog: InventoryDialog

    @Wire
    private lateinit var inputMultiplexer: InputMultiplexer
    @Wire
    private lateinit var playerInputProcessor: PlayerInputProcessor
    @Wire
    private lateinit var uiInputProcessor: UiInputProcessor

    @Wire
    private lateinit var inventoryUI: InventoryUI
    @Wire
    private lateinit var aimUI: AimUI
    @Wire
    private lateinit var dataUI: DataUI

    override fun initialize() {
        stage.addActor(aimUI.getUI())
        stage.addActor(dataUI.getUI())
        stage.addActor(inventoryUI.getUI())
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
        inventoryUI.getUI().isVisible = false
        playerInputProcessor.clear()
        inputMultiplexer.removeProcessor(playerInputProcessor)
        Gdx.input.isCursorCatched = false
        if (!inventoryDialog.isShowed()) inventoryDialog.show(dialogManager)
    }

    @BusEvent
    fun onInventoryClose(event: UiEvent.OnInventoryClose) {
        inventoryUI.getUI().isVisible = true
        inputMultiplexer.addProcessor(playerInputProcessor)
        Gdx.input.isCursorCatched = true
        if (inventoryDialog.isShowed()) inventoryDialog.dismiss()
    }

    override fun processSystem() {
        dataUI.update(world.delta)

        stage.act(world.delta)
        viewport.apply()
        stage.draw()
    }

    override fun dispose() {
        stage.clear()
    }
}