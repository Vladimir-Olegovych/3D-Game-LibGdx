package core.controls

import app.feature.game.dialogs.PauseDialog
import app.feature.game.event.EventBusTypes
import app.feature.game.event.UiEvent
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.InputProcessor
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.general.Context

class UiInputProcessor: LaunchedEffect, InputProcessor {

    private var inPause = false
    private var inInventory = false
    private lateinit var mainEventBus: EventBus

    fun onInventoryDialog() {
        if (inPause)  return
        if (inInventory) {
            mainEventBus.sendEvent(UiEvent.OnInventoryClose)
        } else {
            mainEventBus.sendEvent(UiEvent.OnInventoryOpen)
        }
        inInventory = !inInventory

    }

    fun onPauseDialog(state: PauseDialog.State) {
        if (inInventory) {
            mainEventBus.sendEvent(UiEvent.OnInventoryClose)
            inInventory = !inInventory
            return
        }

        if (inPause) {
            mainEventBus.sendEvent(UiEvent.OnPauseClose(state))
            if (state == PauseDialog.State.QUIT) {
                mainEventBus.sendEvent(UiEvent.OnMenuScreen)
            }
        } else {
            mainEventBus.sendEvent(UiEvent.OnPauseOpen)
        }

        inPause = !inPause
    }

    override fun launch(context: Context) {
        mainEventBus = context.getObject(EventBusTypes.MAIN_EVENT_BUS)
    }

    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        when(keycode) {
            Keys.ESCAPE -> {
                onPauseDialog(PauseDialog.State.RESUME)
            }
            Keys.E -> {
                onInventoryDialog()
            }
        }
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchDown(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        return false
    }

    override fun touchUp(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        return false
    }

    override fun touchCancelled(
        screenX: Int,
        screenY: Int,
        pointer: Int,
        button: Int
    ): Boolean {
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }
}