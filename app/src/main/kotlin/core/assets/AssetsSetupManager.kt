package core.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.mesh.ModelAssetManager
import com.gigapi.sounds.MusicAssetManager
import core.shaders.ShaderTypes

object AssetsSetupManager: LaunchedEffect {

    const val ASSETS_PATH = "app/src/main/resources"

    override fun launch(context: Context) {
        val assetManager = AssetManager()
        assetManager.load(SkinID.BLOCK.atlas, TextureAtlas::class.java)
        SkinID.entries.forEach {
            assetManager.load(it.skin, Skin::class.java)
        }
        TextureID.entries.forEach {
            assetManager.load(it.filePath, Texture::class.java)
        }
        assetManager.finishLoading()
        context.setObject(assetManager)
        //---
        val modelAssetManager = ModelAssetManager("textures", assetManager)
        ModelID.entries.forEach {
            modelAssetManager.loadObj(it, "$ASSETS_PATH/${it.filePathObj}", "$ASSETS_PATH/${it.filePathMlt}")
        }
        context.setObject(modelAssetManager)
        //---
        val musicAssetManager = MusicAssetManager()
        MusicID.entries.forEach {
            musicAssetManager.load(it, "$ASSETS_PATH/${it.filePath}")
        }
        context.setObject(musicAssetManager)
        //---
        //ShaderProgram.pedantic = false
        val simpleShader = ShaderProgram(
            Gdx.files.local("$ASSETS_PATH/shaders/vertex_shader_simple.glsl").readString(),
            Gdx.files.local("$ASSETS_PATH/shaders/fragment_shader_simple.glsl").readString()
        )
        check(simpleShader.isCompiled) { "Simple shader compile error: ${simpleShader.log}" }
        context.setObject(ShaderTypes.SIMPLE_SHADER, simpleShader)
        val sunShader = ShaderProgram(
            Gdx.files.local("$ASSETS_PATH/shaders/vertex_shader_sun.glsl").readString(),
            Gdx.files.local("$ASSETS_PATH/shaders/fragment_shader_sun.glsl").readString()
        )
        check(sunShader.isCompiled) { "Sun shader compile error: ${sunShader.log}" }
        context.setObject(ShaderTypes.SUN_SHADER, sunShader)
    }
}