package app.feature.game.ecs.components

import com.artemis.Component
import core.chunk.ChunkData

class ChunkComponent: Component() {
    var chunkData: ChunkData? = null
}