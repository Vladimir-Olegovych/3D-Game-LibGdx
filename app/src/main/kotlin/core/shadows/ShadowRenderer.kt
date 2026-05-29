package core.shadows

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
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

class ShadowRenderer : LaunchedEffect, DisposableEffect {

    companion object {
        const val SHADOW_MAP_SIZE = 2048 * 2

        // Размер ортографической области теней вокруг камеры
        // Увеличь если тени обрезаются по краям
        const val SHADOW_ORTHO_SIZE = 400f

        // Насколько далеко от камеры рендерить тени (вперёд/назад)
        const val SHADOW_DEPTH_RANGE = 400f

        // Расстояние позиции световой камеры от цели
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

        // Вектор ОТ источника К сцене (для шейдера)
        @JvmStatic
        fun getLightDirForShader(): Vector3 = lightDirection.cpy().nor()

        // Позиция световой камеры = противоположное направление
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

        lightCamera = OrthographicCamera(SHADOW_ORTHO_SIZE, SHADOW_ORTHO_SIZE).apply {
            near = 1f
            far = LIGHT_DISTANCE + SHADOW_DEPTH_RANGE
        }

        shadowFbo = GLFrameBuffer.FrameBufferBuilder(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
            .addColorTextureAttachment(GL30.GL_RGBA32F, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE)
            .addDepthTextureAttachment(GL30.GL_DEPTH_COMPONENT, GL30.GL_FLOAT)
            .build()

        shadowTexture = shadowFbo.colorBufferTexture.apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
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

        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        shadowShader.bind()
        shadowShader.setUniformMatrix("u_projViewWorldTrans", lightViewProjectionMatrix)
    }

    fun end() {
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        Gdx.gl.glCullFace(GL20.GL_BACK)

        Gdx.gl.glDepthFunc(GL20.GL_LESS)
        shadowFbo.end()
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
    }

    fun updateLightCamera(snapToGrid: Boolean = true) {
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
            val texelSize = SHADOW_ORTHO_SIZE * 2f / SHADOW_MAP_SIZE.toFloat()
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
    override fun dispose() {
        shadowFbo.dispose()
    }
}