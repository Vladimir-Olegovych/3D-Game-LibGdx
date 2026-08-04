package core.animator

abstract class AnimationScript(
    protected val context: AnimationContext
) {
    protected var time = 0f

    open fun onStart() {
        time = 0f
    }

    open fun onStop() {}

    abstract fun update(deltaTime: Float)
}
