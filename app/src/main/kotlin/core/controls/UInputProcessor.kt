package core.controls

import com.badlogic.gdx.Input.Keys
import com.gigapi.input.EditableInputProcessor

class UInputProcessor: EditableInputProcessor() {

    interface InputListener {
        fun onInventory() {}
        fun onPause() {}
    }

    private val listeners = mutableSetOf<InputListener>()

    fun addListener(listener: InputListener) {
        listeners += listener
    }

    fun removeListener(listener: InputListener) {
        listeners -= listener
    }

    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        when(keycode) {
            Keys.ESCAPE -> { listeners.forEach { it.onPause() } }
            Keys.E -> { listeners.forEach { it.onInventory() } }
        }
        return false
    }
}