package app.feature.game.event

sealed class HotKeyEvent {
    class OnFreeCamera(val state: Boolean): HotKeyEvent()
    class OnCameraMode(val mode: Int): HotKeyEvent()
}