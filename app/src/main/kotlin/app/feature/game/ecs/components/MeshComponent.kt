package app.feature.game.ecs.components

import com.artemis.Component
import com.badlogic.gdx.graphics.GLTexture
import com.gigapi.effects.DisposableEffect
import com.gigapi.mesh.MeshData

class MeshComponent: DisposableEffect, Component() {
    var meshData: MeshData? = null
    var meshTextureData: GLTexture? = null

    override fun dispose() {
        meshData?.mesh?.dispose()
        meshData = null
    }
}