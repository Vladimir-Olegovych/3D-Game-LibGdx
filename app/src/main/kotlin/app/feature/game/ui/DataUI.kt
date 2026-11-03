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
import com.gigapi.storage.json.AppConfig
import core.assets.SkinID
import core.chunk.ChunkWorldUpdater
import core.configs.ConfigTypes
import core.defaults.CameraTypes
import core.settings.SettingsSO
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
    private lateinit var settingsSO: SettingsSO
    private lateinit var camera: PerspectiveCamera
    private lateinit var fpsLabel: Label
    private lateinit var timeLabel: Label
    private lateinit var positionLabel: Label
    private lateinit var drawRadiusLabel: Label

    override fun launch(gContext: GContext) {
        timeState = gContext.getObject()
        settingsSO = gContext.getObject<AppConfig<SettingsSO>>(ConfigTypes.SETTINGS_SO).getConfig()

        camera = gContext.getObject(CameraTypes.GL_3D)
        val assetManager = gContext.getObject<AssetManager>()
        val skin = assetManager.get<Skin>(SkinID.BUTTON.skin)
        fpsLabel = Label("FPS: Not updated", skin).apply { setAlignment(Align.right) }
        timeLabel = Label("Time: ", skin).apply { setAlignment(Align.right) }
        positionLabel = Label("XYZ: ", skin).apply { setAlignment(Align.right) }
        drawRadiusLabel = Label("Radius XY: ${settingsSO.drawRadiusXZ}|${settingsSO.drawRadiusY}", skin).apply { setAlignment(Align.right) }
        layout.add(fpsLabel).right().row()
        layout.add(timeLabel).right().row()
        layout.add(positionLabel).right().row()
        layout.add(drawRadiusLabel).right()
    }

    private var frameCount = 0
    private var elapsedTime = 0f
    private var currentFps = 0

    fun update(deltaTime: Float) {
        updateTime()
        updateDrawRadius()
        updateFPS(deltaTime)
        updatePosition()
    }

    private fun updatePosition() {
        val pos = camera.position
        positionLabel.setText(
            "XYZ: ${floor(pos.x).toInt()}|${floor(pos.y).toInt()}|${floor(pos.z).toInt()}"
        )
    }

    private fun updateDrawRadius() {
        timeLabel.setText("Time: ${String.format("%.1f", timeState.timeOfDay)}")
        drawRadiusLabel.setText("Radius XY: ${settingsSO.drawRadiusXZ}|${settingsSO.drawRadiusY}")
    }

    private fun updateTime() {
        timeLabel.setText("Time: ${String.format("%.1f", timeState.timeOfDay)}")
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
