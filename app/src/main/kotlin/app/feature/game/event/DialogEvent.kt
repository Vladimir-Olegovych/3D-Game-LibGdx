package app.feature.game.event

import app.feature.game.dialogs.PauseDialog

sealed class DialogEvent {
    object OnPauseOpen: DialogEvent()
    class OnPauseClose(val state: PauseDialog.State): DialogEvent()
    object OnInventoryOpen: DialogEvent()
    object OnInventoryClose: DialogEvent()
    object OnMenuScreen: DialogEvent()
}