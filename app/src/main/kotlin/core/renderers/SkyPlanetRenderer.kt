package core.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import core.defaults.CameraTypes
import core.shaders.ShaderTypes
import app.feature.game.ecs.states.TimeState

class SkyPlanetRenderer : LaunchedEffect, DisposableEffect {

    private lateinit var sunShader: ShaderProgram
    private lateinit var camera: PerspectiveCamera
    private lateinit var timeState: TimeState
    private lateinit var mesh: Mesh

    private val sunColor = floatArrayOf(1.0f, 0.95f, 0.3f, 1.0f)
    private val moonColor = floatArrayOf(0.88f, 0.9f, 0.98f, 1.0f)
    private val sunRadius = 0.06f
    private val moonRadius = 0.035f

    private val tmpDirection = Vector3()
    private val tmpPosition = Vector3()
    private val screenPosBuffer = floatArrayOf(0f, 0f)

    private var disposed = false

    override fun launch(gContext: GContext) {
        sunShader = gContext.getObject(ShaderTypes.SKY_PLANET_SHADER)
        camera = gContext.getObject(CameraTypes.GL_3D)
        timeState = gContext.getObject()
        mesh = createFullscreenQuad()
    }

    fun render() {
        if (disposed) return

        renderBody(
            direction = timeState.getSunDirection(tmpDirection),
            worldPosition = timeState.getSunWorldPosition(camera.position, tmpPosition),
            radius = sunRadius,
            color = sunColor
        )
        renderBody(
            direction = timeState.getMoonDirection(tmpDirection),
            worldPosition = timeState.getMoonWorldPosition(camera.position, tmpPosition),
            radius = moonRadius,
            color = moonColor
        )
    }

    private fun renderBody(
        direction: Vector3,
        worldPosition: Vector3,
        radius: Float,
        color: FloatArray
    ) {
        val visibility = timeState.celestialVisibility(direction)
        if (visibility < 0.001f) return

        val projected = camera.project(worldPosition)
        if (projected.z < 0f) return

        screenPosBuffer[0] = (projected.x / Gdx.graphics.width) * 2f - 1f
        screenPosBuffer[1] = (projected.y / Gdx.graphics.height) * 2f - 1f

        val aspectRatio = Gdx.graphics.width.toFloat() / Gdx.graphics.height.toFloat()
        color[3] = visibility

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(false)

        sunShader.bind()
        sunShader.setUniform2fv("u_sunScreenPos", screenPosBuffer, 0, 2)
        sunShader.setUniformf("u_radius", radius)
        sunShader.setUniformf("u_aspectRatio", aspectRatio)
        sunShader.setUniform4fv("u_sunColor", color, 0, 4)

        mesh.render(sunShader, GL20.GL_TRIANGLE_FAN)

        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun createFullscreenQuad(): Mesh {
        val mesh = Mesh(
            true, 4, 0,
            VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
        )
        mesh.setVertices(floatArrayOf(
            -1f, -1f,  0f, 0f,
            1f, -1f,  1f, 0f,
            1f,  1f,  1f, 1f,
            -1f,  1f,  0f, 1f,
        ))
        return mesh
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        mesh.dispose()
    }
}
