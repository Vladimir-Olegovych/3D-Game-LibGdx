package core.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.fasterxml.jackson.databind.ObjectMapper
import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.mesh.ModelAssetManager
import com.gigapi.sounds.MusicAssetManager
import core.shaders.ShaderTypes

object AssetsSetupManager: LaunchedEffect {

    override fun launch(gContext: GContext) {
        val om = ObjectMapper()
        gContext.setObject(om)
        //---
        val assetManager = AssetManager()
        assetManager.load(SkinID.BLOCK.atlas, TextureAtlas::class.java)
        SkinID.entries.forEach {
            assetManager.load(it.skin, Skin::class.java)
        }
        TextureID.entries.forEach {
            assetManager.load(it.filePath, Texture::class.java)
        }
        assetManager.finishLoading()
        gContext.setObject(assetManager)
        //---
        val modelAssetManager = ModelAssetManager("textures", assetManager)
        ModelID.entries.forEach {
            if (it != ModelID.NULL)
                modelAssetManager.loadObj(it, it.filePathObj, it.filePathMlt)
        }
        gContext.setObject(modelAssetManager)
        //---
        val musicAssetManager = MusicAssetManager()
        MusicID.entries.forEach {
            musicAssetManager.load(it, it.filePath)
        }
        gContext.setObject(musicAssetManager)
        //---
        //ShaderProgram.pedantic = false
        val chunkShader = ShaderProgram(
            Gdx.files.internal("shaders/chunk/vertex_shader_chunk.glsl").readString(),
            Gdx.files.internal("shaders/chunk/fragment_shader_chunk.glsl").readString()
        )
        gContext.setObject(ShaderTypes.CHUNK_SHADER, chunkShader)
        val modelShader = ShaderProgram(
            Gdx.files.internal("shaders/model/vertex_shader_model.glsl").readString(),
            Gdx.files.internal("shaders/model/fragment_shader_model.glsl").readString()
        )
        gContext.setObject(ShaderTypes.MODEL_SHADER, modelShader)
        val sunShader = ShaderProgram(
            Gdx.files.internal("shaders/sky_planet/vertex_shader_sky_planet.glsl").readString(),
            Gdx.files.internal("shaders/sky_planet/fragment_shader_sky_planet.glsl").readString()
        )
        gContext.setObject(ShaderTypes.SKY_PLANET_SHADER, sunShader)
        val starShader = ShaderProgram(
            Gdx.files.internal("shaders/stars/vertex_shader_stars.glsl").readString(),
            Gdx.files.internal("shaders/stars/fragment_shader_stars.glsl").readString()
        )
        gContext.setObject(ShaderTypes.STAR_SHADER, starShader)
    }
}
