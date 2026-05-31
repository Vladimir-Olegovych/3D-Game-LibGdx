package core.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.defaults.CameraTypes
import core.shaders.ShaderTypes

class SunRenderer : LaunchedEffect, DisposableEffect {

    private lateinit var sunShader: ShaderProgram
    private lateinit var camera: PerspectiveCamera
    private lateinit var mesh: Mesh

    private val sunColor = floatArrayOf(1.0f, 0.95f, 0.3f, 1.0f)
    private val sunRadius = 0.06f
    private val tmpVec = Vector3()

    override fun launch(context: Context) {
        sunShader = context.getObject(ShaderTypes.SUN_SHADER)
        camera = context.getObject(CameraTypes.GL_3D)
        mesh = createFullscreenQuad()
    }
    fun render(lightPosition: Vector3) {
        val sunPosition = lightPosition.cpy()
        val screenPos = camera.project(sunPosition)

        val screenX = (screenPos.x / Gdx.graphics.width) * 2f - 1f
        val screenY = (screenPos.y / Gdx.graphics.height) * 2f - 1f

        val aspectRatio = Gdx.graphics.width.toFloat() / Gdx.graphics.height.toFloat()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDepthMask(false)

        sunShader.bind()
        sunShader.setUniform2fv("u_sunScreenPos", floatArrayOf(screenX, screenY), 0, 2)
        sunShader.setUniformf("u_radius", sunRadius)
        sunShader.setUniformf("u_aspectRatio", aspectRatio)
        sunShader.setUniform4fv("u_sunColor", sunColor, 0, 4)

        mesh.render(sunShader, GL20.GL_TRIANGLE_FAN)

        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDepthMask(true)
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
        mesh.dispose()
    }
}