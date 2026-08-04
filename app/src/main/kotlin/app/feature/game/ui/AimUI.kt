package app.feature.game.ui

import app.feature.game.event.EventBusTypes
import app.feature.game.event.HotKeyEvent
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import core.controls.PlayerInputProcessor
import core.ui.UIGetter

class AimUI : DisposableEffect, LaunchedEffect, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        center()
    }

    private var texture: Texture? = null

    @BusEvent
    fun onCameraMode(event: HotKeyEvent.OnCameraMode) {
        layout.isVisible = event.mode == PlayerInputProcessor.VIEW_FIRST_PERSON
    }

    override fun dispose() {
        texture?.dispose()
    }

    override fun launch(gContext: GContext) {
        val eventBus = gContext.getObject<EventBus>(EventBusTypes.MAIN_EVENT_BUS)
        eventBus.registerHandler(this)
        texture = createCrosshairTexture(size = 16, thickness = 2, gap = 2)
        layout.add(Image(texture))
    }

    override fun getUI(): Actor = layout

    private fun createCrosshairTexture(size: Int, thickness: Int, gap: Int): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pixmap.setColor(0f, 0f, 0f, 0f)
        pixmap.fill()
        pixmap.setColor(1f, 1f, 1f, 1f)

        val arm = (size - gap) / 2
        val offset = (size - thickness) / 2

        // Horizontal: left + right
        pixmap.fillRectangle(0, offset, arm, thickness)
        pixmap.fillRectangle(arm + gap, offset, arm, thickness)
        // Vertical: top + bottom
        pixmap.fillRectangle(offset, 0, thickness, arm)
        pixmap.fillRectangle(offset, arm + gap, thickness, arm)

        val result = Texture(pixmap)
        result.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)
        pixmap.dispose()
        return result
    }
}
