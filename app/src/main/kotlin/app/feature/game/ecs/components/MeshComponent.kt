package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.graphics.GLTexture
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.screens.mesh.MeshData

class MeshComponent: DisposableEffect, Component() {
    var meshData: MeshData? = null
    var meshTextureData: GLTexture? = null

    override fun dispose() {
        meshData?.mesh?.dispose()
        meshData = null
    }
}