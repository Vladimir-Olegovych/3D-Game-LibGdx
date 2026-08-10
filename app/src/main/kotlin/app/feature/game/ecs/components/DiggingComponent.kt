package app.feature.game.ecs.components

import com.artemis.Component
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.items.ToolType

class DiggingComponent: Component() {
    var chunkPosition: IntVector3 = IntVector3()
    var blockPosition: IntVector3 = IntVector3()
    var blockToSet: BlockType = BlockType.AIR
    var blockToRemove: BlockType = BlockType.AIR
    var miningToolType: ToolType? = null
    var miningToolLevel: Int? = null
    var digTime = 0f
    var process = 0f
}