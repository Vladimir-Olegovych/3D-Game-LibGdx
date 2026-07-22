package core.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.Context
import core.defaults.CameraTypes
import core.shaders.ShaderTypes
import core.time.TimeState

class StarRenderer : LaunchedEffect, DisposableEffect {

    private lateinit var starShader: ShaderProgram
    private lateinit var camera: PerspectiveCamera
    private lateinit var timeState: TimeState
    private lateinit var mesh: Mesh

    private val invViewProj = Matrix4()
    private var disposed = false
    private var elapsedTime = 0f

    override fun launch(context: Context) {
        starShader = context.getObject(ShaderTypes.STAR_SHADER)
        camera = context.getObject(CameraTypes.GL_3D)
        timeState = context.getObject()
        mesh = createFullscreenQuad()
    }

    fun render() {
        if (disposed) return
        val nightFactor = timeState.starVisibility()
        if (nightFactor < 0.001f) return

        elapsedTime += Gdx.graphics.deltaTime
        invViewProj.set(camera.combined).inv()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(false)

        starShader.bind()
        starShader.setUniformMatrix("u_invViewProj", invViewProj)
        starShader.setUniformf("u_nightFactor", nightFactor)
        starShader.setUniformf("u_skyRotation", timeState.skyRotation())
        starShader.setUniformf("u_time", elapsedTime)

        mesh.render(starShader, GL20.GL_TRIANGLE_FAN)

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
