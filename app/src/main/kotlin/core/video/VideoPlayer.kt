package core.video

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FillViewport
import com.gigapi.video.GigVideoPlayer
import core.assets.AssetsSetupManager

class VideoPlayer(
    val fileName: String,
    val repeatable: Boolean = true,
    private val startVolume: Float = 0.4f
) : Disposable {

    val videoPlayer = GigVideoPlayer(repeatable)
    private val spriteBatch = SpriteBatch()
    private val viewport = FillViewport(1f, 1f)
    private var isViewportInitialized = false

    init {
        val videoFile: FileHandle = Gdx.files.local("${AssetsSetupManager.ASSETS_PATH}/video/$fileName")

        try {
            videoPlayer.load(videoFile)
            videoPlayer.play()
        } catch (e: Exception) {
            Gdx.app.error("VideoPlayer", "Failed to load video: $fileName", e)
        }
    }

    fun onInitializeVideo(texture: Texture) {
        videoPlayer.setVolume(startVolume)
        val videoWidth = texture.width.toFloat()
        val videoHeight = texture.height.toFloat()

        viewport.setWorldSize(videoWidth, videoHeight)
    }

    fun render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        val texture = videoPlayer.getTexture() ?: return

        if (!isViewportInitialized) {
            onInitializeVideo(texture)
            isViewportInitialized = true
        }

        viewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

        spriteBatch.projectionMatrix = viewport.camera.combined
        spriteBatch.begin()

        spriteBatch.draw(texture, 0f, 0f, viewport.worldWidth, viewport.worldHeight)

        spriteBatch.end()
    }

    override fun dispose() {
        videoPlayer.dispose()
        spriteBatch.dispose()
    }
}