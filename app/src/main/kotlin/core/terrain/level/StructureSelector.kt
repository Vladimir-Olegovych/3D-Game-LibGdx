package core.terrain.level

import com.gigapi.math.vector.IntVector3
import core.terrain.biome.BiomeGenerator
import core.terrain.biome.models.BiomeType
import kotlin.random.Random

class StructureSelector(
    private val biomeGenerators: Map<BiomeType, BiomeGenerator>,
    private val structureGenerators: Map<StructureType, StructureGenerator>
) {

    companion object {
        const val STRUCTURE_RANGE = 32
    }

    private val random = Random(0)

    fun generateStructures(position: IntVector3) {

    }

}