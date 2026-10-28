package core.bullet.raycast

import com.gigapi.math.vector.IntVector3
import core.blocks.types.BlockType

class VoxelHit(
    val chunkPosition: IntVector3,
    val chunkEntityId: Int,
    val blockPosition: IntVector3,
    val block: BlockType
)
