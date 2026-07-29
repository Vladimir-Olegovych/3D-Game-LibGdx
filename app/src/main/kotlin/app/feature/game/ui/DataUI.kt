package app.feature.game.ui

import app.feature.game.ecs.states.TimeState
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import core.assets.SkinID
import core.defaults.CameraTypes
import core.ui.UIGetter
import kotlin.math.floor

class DataUI : LaunchedEffect, UIGetter {

    private val layout = Table().apply {
        setFillParent(true)
        top()
        right()
        defaults().right()
    }
    private lateinit var timeState: TimeState
    private lateinit var camera: PerspectiveCamera
    private lateinit var fpsLabel: Label
    private lateinit var timeLabel: Label
    private lateinit var positionLabel: Label

    override fun launch(context: GContext) {
        timeState = context.getObject()
        camera = context.getObject(CameraTypes.GL_3D)
        val assetManager = context.getObject<AssetManager>()
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        fpsLabel = Label("FPS: Not updated", skin).apply { setAlignment(Align.right) }
        timeLabel = Label("Time: ", skin).apply { setAlignment(Align.right) }
        positionLabel = Label("XYZ: ", skin).apply { setAlignment(Align.right) }
        layout.add(fpsLabel).right().row()
        layout.add(timeLabel).right().row()
        layout.add(positionLabel).right()
    }

    private var frameCount = 0
    private var elapsedTime = 0f
    private var currentFps = 0

    fun update(deltaTime: Float) {
        updateTime()
        updateFPS(deltaTime)
        updatePosition()
    }

    private fun updatePosition() {
        val pos = camera.position
        positionLabel.setText(
            "XYZ: ${floor(pos.x).toInt()}|${floor(pos.y).toInt()}|${floor(pos.z).toInt()}"
        )
    }

    private fun updateTime() {
        timeLabel.setText("Time: ${String.format("%.1f", timeState.dayPhase)}")
    }

    private fun updateFPS(deltaTime: Float) {
        frameCount++
        elapsedTime += deltaTime

        if (elapsedTime >= 1f) {
            currentFps = frameCount
            frameCount = 0
            elapsedTime = 0f

            if (currentFps < 60) {
                fpsLabel.setColor(1f, 0f, 0f, 1f)
            } else {
                fpsLabel.setColor(1f, 1f, 1f, 1f)
            }

            fpsLabel.setText("FPS: $currentFps")
        }
    }
    override fun getUI(): Actor = layout
}
