package app.feature.game.event

import app.feature.game.dialogs.PauseDialog

sealed class UiEvent {
    object OnPauseOpen: UiEvent()
    class OnPauseClose(val state: PauseDialog.State): UiEvent()
    object OnInventoryOpen: UiEvent()
    object OnInventoryClose: UiEvent()
    object OnMenuScreen: UiEvent()
}