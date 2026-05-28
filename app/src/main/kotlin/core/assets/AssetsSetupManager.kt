package core.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.screens.mesh.ModelAssetManager
import core.shaders.ShaderTypes

object AssetsSetupManager: LaunchedEffect {

    const val ASSETS_PATH = "app/src/main/resources"

    override fun launch(context: Context) {
        val assetManager = AssetManager()
        SkinID.entries.forEach {
            assetManager.load(it.skin, Skin::class.java)
        }
        TextureID.entries.forEach {
            assetManager.load(it.filePath, Texture::class.java)
        }
        assetManager.finishLoading()
        context.setObject(assetManager)

        val modelAssetManager = ModelAssetManager()
        ModelID.entries.forEach {
            modelAssetManager.loadObj(it, "$ASSETS_PATH/${it.filePath}")
        }
        context.setObject(modelAssetManager)
        //---
        val simpleShader = ShaderProgram(
            Gdx.files.local("$ASSETS_PATH/shaders/vertex_shader_simple.glsl").readString(),
            Gdx.files.local("$ASSETS_PATH/shaders/fragment_shader_simple.glsl").readString()
        )
        context.setObject(ShaderTypes.SIMPLE_SHADER, simpleShader)
    }
}