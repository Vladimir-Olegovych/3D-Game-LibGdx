package core.terrain.structures

import core.blocks.BlockType
import core.terrain.level.StructureGenerator

class TreeStructure: StructureGenerator() {
    private val bytes = ByteArray(4) {
        BlockType.STONE.id
    }
    override fun onGetStructure(): ByteArray {
        return bytes
    }
}