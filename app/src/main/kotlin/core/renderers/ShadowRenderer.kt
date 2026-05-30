package core.renderers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import core.defaults.CameraTypes
import core.shaders.ShaderTypes
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

class ShadowRenderer : LaunchedEffect, DisposableEffect {

    companion object {
        const val SHADOW_MAP_SIZE = 2048 * 2
        const val SHADOW_DEPTH_RANGE = 400f
        const val LIGHT_DISTANCE = 4000f

        @JvmStatic
        var lightDirection = Vector3(1f, -1f, 0f).nor()!!
            private set

        @JvmStatic
        val normalizedLightDirection: Vector3
            get() = lightDirection.cpy().nor()

        @JvmStatic
        fun setLightDirection(x: Float, y: Float, z: Float) {
            lightDirection.set(x, y, z).nor()
        }

        @JvmStatic
        fun setLightDirection(direction: Vector3) {
            lightDirection.set(direction).nor()
        }

        @JvmStatic
        fun getOppositeLightDirection(): Vector3 = lightDirection.cpy().scl(-1f)
    }

    lateinit var shadowFbo: FrameBuffer
    lateinit var shadowTexture: Texture
    lateinit var shadowShader: ShaderProgram
    lateinit var lightViewProjectionMatrix: Matrix4

    private lateinit var lightCamera: OrthographicCamera
    private lateinit var mainCamera: PerspectiveCamera

    private val tmpTarget = Vector3()
    private val tmpPosition = Vector3()
    private val tmpUp = Vector3()

    override fun launch(context: Context) {
        shadowShader = context.getObject(ShaderTypes.SHADOW_SHADER)
        mainCamera = context.getObject(CameraTypes.GL_3D)

        lightCamera = OrthographicCamera(SHADOW_DEPTH_RANGE, SHADOW_DEPTH_RANGE).apply {
            near = 1f
            far = LIGHT_DISTANCE + SHADOW_DEPTH_RANGE
        }

        shadowFbo = GLFrameBuffer.FrameBufferBuilder(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
            .addColorTextureAttachment(GL30.GL_RGBA32F, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE)
            .addDepthTextureAttachment(GL30.GL_DEPTH_COMPONENT, GL30.GL_FLOAT)
            .build()

        shadowTexture = shadowFbo.colorBufferTexture.apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
            setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }

        lightViewProjectionMatrix = Matrix4()
        updateLightCamera()
    }

    fun begin() {
        updateLightCamera()
        shadowFbo.begin()
        Gdx.gl.glViewport(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)

        shadowShader.bind()
        shadowShader.setUniformMatrix("u_projViewWorldTrans", lightViewProjectionMatrix)
    }

    fun end() {
        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)

        shadowFbo.end()
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
    }
    //var x = 0.5F
    //private var timeAccumulator = 0F
    fun updateLightCamera(snapToGrid: Boolean = true) {
        //timeAccumulator += Gdx.graphics.deltaTime
        //val newX = 0.5F + sin(timeAccumulator.toDouble()).toFloat() * 0.1F

        //setLightDirection(1F, -newX, 0F)
        tmpTarget.set(mainCamera.position)

        tmpPosition.set(getOppositeLightDirection())
            .scl(LIGHT_DISTANCE)
            .add(tmpTarget)

        lightCamera.position.set(tmpPosition)

        val lightDir = normalizedLightDirection
        val absY = abs(lightDir.y)
        tmpUp.set(if (absY > 0.99f) Vector3.Z else Vector3.Y)

        val forward = tmpTarget.cpy().sub(lightCamera.position).nor()
        val right = tmpUp.cpy().crs(forward).nor()
        val up = forward.cpy().crs(right).nor()

        lightCamera.direction.set(forward)
        lightCamera.up.set(up)
        lightCamera.update()

        if (snapToGrid) {
            val texelSize = SHADOW_DEPTH_RANGE * 2f / SHADOW_MAP_SIZE.toFloat()
            val projRight = lightCamera.position.dot(right)
            val projUp = lightCamera.position.dot(up)

            val snappedRight = floor(projRight / texelSize) * texelSize
            val snappedUp = floor(projUp / texelSize) * texelSize

            val deltaRight = right.scl(snappedRight - projRight)
            val deltaUp = up.scl(snappedUp - projUp)
            val delta = deltaRight.add(deltaUp)

            lightCamera.position.add(delta)

            lightCamera.update()
        }

        lightViewProjectionMatrix.set(lightCamera.combined)
    }

    fun getLightCameraPosition(): Vector3 = lightCamera.position

    override fun dispose() {
        shadowFbo.dispose()
    }
}