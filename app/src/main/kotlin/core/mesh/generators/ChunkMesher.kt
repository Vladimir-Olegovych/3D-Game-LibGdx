package core.mesh.generators

import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import com.gigapi.mesh.RawMeshData
import core.blocks.BlockDataManager
import core.blocks.types.BlockType
import core.blocks.TextureData
import core.chunk.ChunkData
import core.light.LightProps
import core.mesh.Direction
import core.mesh.DirectionType
import core.mesh.MeshUtils
import core.mesh.VertexAttribute3

class ChunkMesher : MeshGenerator, LaunchedEffect {

    companion object {
        val directions = listOf(
            Direction(1, 0, 0, VertexAttribute3.Normal(1F, 0f, 0f), DirectionType.RIGHT),
            Direction(-1, 0, 0, VertexAttribute3.Normal(-1F, 0f, 0f), DirectionType.LEFT),
            Direction(0, 1, 0, VertexAttribute3.Normal(0f, 1F, 0f), DirectionType.UP),
            Direction(0, -1, 0, VertexAttribute3.Normal(0f, -1F, 0f), DirectionType.DOWN),
            Direction(0, 0, 1, VertexAttribute3.Normal(0f, 0f, 1F), DirectionType.FRONT),
            Direction(0, 0, -1, VertexAttribute3.Normal(0f, 0f, -1F), DirectionType.BACK)
        )
    }

    private lateinit var blockDataManager: BlockDataManager

    override fun launch(gContext: GContext) {
        blockDataManager = gContext.getObject()
    }

    /**
     * 3x3x3 соседей чанка, найденных один раз на чанк.
     * Раньше на КАЖДУЮ грань и на каждый AO-запрос создавался IntVector3 и шёл lookup в HashMap.
     */
    class Neighborhood(center: ChunkData, chunkMap: Map<IntVector3, ChunkData>) {
        private val w = center.chunkWidth
        private val h = center.chunkHeight
        private val chunks = arrayOfNulls<ChunkData>(27)

        init {
            val p = center.position
            for (dz in -1..1) for (dy in -1..1) for (dx in -1..1) {
                val idx = (dx + 1) + (dy + 1) * 3 + (dz + 1) * 9
                chunks[idx] = if (dx == 0 && dy == 0 && dz == 0) {
                    center
                } else {
                    chunkMap[IntVector3(p.x + dx, p.y + dy, p.z + dz)]
                }
            }
        }

        /** Допустимы координаты от -1 до size (соседний чанк), как и в оригинале. */
        fun block(nx: Int, ny: Int, nz: Int): BlockType {
            val cx = if (nx < 0) 0 else if (nx >= w) 2 else 1
            val cy = if (ny < 0) 0 else if (ny >= h) 2 else 1
            val cz = if (nz < 0) 0 else if (nz >= w) 2 else 1
            val chunk = chunks[cx + cy * 3 + cz * 9] ?: return BlockType.NOTHING
            val lx = if (nx < 0) nx + w else if (nx >= w) nx - w else nx
            val ly = if (ny < 0) ny + h else if (ny >= h) ny - h else ny
            val lz = if (nz < 0) nz + w else if (nz >= w) nz - w else nz
            return chunk.getBlockByLocal(lx, ly, lz)
        }
        /** packed (sky shl 4 | block), либо -1 если клетка непрозрачна или чанк ещё не просчитан. */
        fun light(nx: Int, ny: Int, nz: Int): Int {
            val cx = if (nx < 0) 0 else if (nx >= w) 2 else 1
            val cy = if (ny < 0) 0 else if (ny >= h) 2 else 1
            val cz = if (nz < 0) 0 else if (nz >= w) 2 else 1
            val chunk = chunks[cx + cy * 3 + cz * 9] ?: return -1
            val arr = chunk.light ?: return -1
            val lx = if (nx < 0) nx + w else if (nx >= w) nx - w else nx
            val ly = if (ny < 0) ny + h else if (ny >= h) ny - h else ny
            val lz = if (nz < 0) nz + w else if (nz >= w) nz - w else nz
            val idx = chunk.getIndex(lx, ly, lz)
            if (LightProps.opacity(chunk.blocks[idx]) >= 15) return -1
            return arr[idx].toInt() and 0xFF
        }
    }

    override fun createMesh(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData
    ): RawMeshData {
        val w = chunkData.chunkWidth
        val h = chunkData.chunkHeight

        if (chunkData.isEmpty) return RawMeshData(FloatArray(0), ShortArray(0))

        val textureMap = blockDataManager.getBlockTextureDataMap()
        val nb = Neighborhood(chunkData, chunkMap)

        // Лямбда создаётся один раз, а не на каждую грань.
        val blockExists: (Int, Int, Int) -> Boolean = { wx, wy, wz ->
            when (nb.block(wx, wy, wz)) {
                BlockType.AIR, BlockType.NOTHING -> false
                else -> true
            }
        }
        val lightAt: (Int, Int, Int) -> Int = { x, y, z ->
            nb.light(x, y, z)
        }
        // ВАЖНО: чтобы убрать боксинг Float/Short, поменяй типы параметров
        // MeshUtils.addChunkFace(verticesList: ArrayList<Float>, indicesList: ArrayList<Short>)
        // на FloatBuf / ShortBuf из core.lod (у них есть add() и size). Если там больше ничего
        // кроме add/size не используется — других правок не нужно.
        val verticesList = ArrayList<Float>()
        val indicesList = ArrayList<Short>()

        val dirs = directions

        for (x in 0 until w) {
            for (y in 0 until h) {
                for (z in 0 until w) {
                    val block = chunkData.getBlockByLocal(x, y, z)
                    if (block == BlockType.AIR) continue
                    val blockData = textureMap[block] ?: continue

                    val currentAllSides = hasActiveAllSides(
                        block, blockData, textureMap, chunkData, nb, x, y, z, w, h
                    )

                    for (i in dirs.indices) {
                        val dir = dirs[i]
                        val nx = x + dir.dx
                        val ny = y + dir.dy
                        val nz = z + dir.dz
                        val neighborBlock = nb.block(nx, ny, nz)

                        val shouldRenderFace =
                            neighborBlock == BlockType.AIR ||
                                    neighborBlock == BlockType.NOTHING ||
                                    currentAllSides ||
                                    (textureMap[neighborBlock]?.generateAllSides == true && !blockData.generateAllSides)
                        if (!shouldRenderFace) continue

                        MeshUtils.addChunkFace(
                            blockDataManager = blockDataManager,
                            verticesList = verticesList,
                            indicesList = indicesList,
                            x, y, z,
                            normal = dir.normal,
                            blockType = block,
                            directionType = dir.directionType,
                            lightAt = lightAt,
                            blockExists = blockExists
                        )
                    }
                }
            }
        }

        return RawMeshData(verticesList.toFloatArray(), indicesList.toShortArray())
    }

    private fun hasActiveAllSides(
        block: BlockType,
        blockData: TextureData,
        textureMap: Map<BlockType, TextureData>,
        chunkData: ChunkData,
        nb: Neighborhood,
        x: Int, y: Int, z: Int,
        w: Int, h: Int,
    ): Boolean {
        if (!blockData.generateAllSides) return false

        val worldX = chunkData.position.x * w + x
        val worldY = chunkData.position.y * h + y
        val worldZ = chunkData.position.z * w + z

        val dirs = directions
        for (i in dirs.indices) {
            val dir = dirs[i]
            val nx = x + dir.dx
            val ny = y + dir.dy
            val nz = z + dir.dz
            val neighbor = nb.block(nx, ny, nz)
            if (neighbor != block) continue
            val neighborData = textureMap[neighbor] ?: continue
            if (!neighborData.generateAllSides) continue

            val nWorldX = chunkData.position.x * w + nx
            val nWorldY = chunkData.position.y * h + ny
            val nWorldZ = chunkData.position.z * w + nz
            if (compareWorldPos(nWorldX, nWorldY, nWorldZ, worldX, worldY, worldZ) < 0) {
                return false
            }
        }
        return true
    }

    private fun compareWorldPos(
        ax: Int, ay: Int, az: Int,
        bx: Int, by: Int, bz: Int,
    ): Int {
        if (ax != bx) return ax.compareTo(bx)
        if (ay != by) return ay.compareTo(by)
        return az.compareTo(bz)
    }
}