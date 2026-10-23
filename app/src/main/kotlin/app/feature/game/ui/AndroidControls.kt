package app.feature.game.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import core.controls.PInputProcessor

/**
 * Простое управление для Android:
 *  - левый джойстик  -> движение
 *  - свайп справа    -> камера
 *  - кнопка справа   -> прыжок
 */
class AndroidControls(
    private val input: PInputProcessor,
    private val stage: Stage
) {

    companion object {
        private const val LOOK_SENSITIVITY = 4f // множитель к CAMERA_SENSITIVITY
    }

    private val textures = ArrayList<Texture>()
    private val actors = ArrayList<Actor>()

    private fun circle(size: Int, color: Color): TextureRegionDrawable {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.setColor(color)
        pm.fillCircle(size / 2, size / 2, size / 2 - 1)
        val tex = Texture(pm)
        pm.dispose()
        textures.add(tex)
        return TextureRegionDrawable(tex)
    }

    fun show() {
        val w = stage.width
        val h = stage.height

        // 1. Зона камеры (правая половина экрана) — добавляется первой, значит лежит ниже кнопки
        val lookArea = Actor().apply {
            setBounds(w / 2f, 0f, w / 2f, h)
            addListener(object : InputListener() {
                private var lastX = 0f
                private var lastY = 0f

                override fun touchDown(e: InputEvent, x: Float, y: Float, p: Int, b: Int): Boolean {
                    lastX = x; lastY = y
                    return true
                }

                override fun touchDragged(e: InputEvent, x: Float, y: Float, p: Int) {
                    // y в scene2d идёт вверх, в мыши вниз — поэтому инвертируем
                    input.addLook((x - lastX) * LOOK_SENSITIVITY, (lastY - y) * LOOK_SENSITIVITY)
                    lastX = x; lastY = y
                }
            })
        }

        // 2. Джойстик движения
        val style = Touchpad.TouchpadStyle().apply {
            background = circle(200, Color(1f, 1f, 1f, 0.25f))
            knob = circle(80, Color(1f, 1f, 1f, 0.6f))
        }
        val touchpad = Touchpad(10f, style).apply {
            setBounds(40f, 40f, 200f, 200f)
            addListener(object : InputListener() {
                override fun touchDown(e: InputEvent, x: Float, y: Float, p: Int, b: Int) = true
            })
        }
        touchpad.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                input.setMoveInput(touchpad.knobPercentX, touchpad.knobPercentY)
            }
        })

        // 3. Кнопка прыжка
        val jumpBtn = Image(circle(120, Color(1f, 1f, 1f, 0.35f))).apply {
            setBounds(w - 160f, 40f, 120f, 120f)
            addListener(object : InputListener() {
                override fun touchDown(e: InputEvent, x: Float, y: Float, p: Int, b: Int): Boolean {
                    input.setJump(true); return true
                }
                override fun touchUp(e: InputEvent, x: Float, y: Float, p: Int, b: Int) {
                    input.setJump(false)
                }
            })
        }

        listOf(lookArea, touchpad, jumpBtn).forEach {
            stage.addActor(it)
            actors.add(it)
        }
    }

    fun hide() {
        actors.forEach { it.remove() }
        actors.clear()
        input.setMoveInput(0f, 0f)
        input.setJump(false)
    }

    fun dispose() {
        hide()
        textures.forEach { it.dispose() }
        textures.clear()
    }
}
