package core.mesh

import com.gigapi.effects.LaunchedEffect
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import com.gigapi.mesh.RawMeshData
import core.blocks.BlockDataManager
import core.blocks.BlockType
import core.blocks.TextureData
import core.chunk.ChunkData

class MeshHelper: MeshGenerator, LaunchedEffect {

    companion object {
        val directions = listOf(
            Direction( 1, 0, 0, VertexAttribute3.Normal(1F, 0f, 0f), DirectionType.RIGHT),
            Direction(-1, 0, 0, VertexAttribute3.Normal(-1F, 0f, 0f), DirectionType.LEFT),
            Direction( 0, 1, 0, VertexAttribute3.Normal(0f, 1F, 0f), DirectionType.UP),
            Direction( 0,-1, 0, VertexAttribute3.Normal(0f,-1F, 0f), DirectionType.DOWN),
            Direction( 0, 0, 1, VertexAttribute3.Normal(0f, 0f, 1F), DirectionType.FRONT),
            Direction( 0, 0,-1, VertexAttribute3.Normal(0f, 0f,-1F), DirectionType.BACK)
        )
    }

    private lateinit var blockDataManager: BlockDataManager

    override fun launch(context: Context) {
        blockDataManager = context.getObject()
    }

    override fun createMesh(
        chunkMap: Map<IntVector3, ChunkData>,
        chunkData: ChunkData
    ): RawMeshData {
        val w = chunkData.chunkWidth
        val h = chunkData.chunkHeight
        val textureMap = blockDataManager.getBlockTextureDataMap()

        val verticesList = ArrayList<Float>()
        val indicesList = ArrayList<Short>()

        for (x in 0 until w) {
            for (y in 0 until h) {
                for (z in 0 until w) {
                    val block = chunkData.getBlockByLocal(x, y, z)
                    if (block == BlockType.AIR) continue
                    val blockData = textureMap[block] ?: continue

                    val currentAllSides = hasActiveAllSides(
                        block = block,
                        blockData = blockData,
                        textureMap = textureMap,
                        chunkData = chunkData,
                        chunkMap = chunkMap,
                        x = x,
                        y = y,
                        z = z,
                        w = w,
                        h = h,
                    )

                    for (dir in directions) {
                        val nx = x + dir.dx
                        val ny = y + dir.dy
                        val nz = z + dir.dz
                        val neighborBlock = getNeighborBlock(
                            chunkData, chunkMap,
                            nx, ny, nz, w, h
                        )
                        val neighborBlockData = textureMap[neighborBlock]

                        val shouldRenderFace =
                            neighborBlock == BlockType.AIR ||
                            neighborBlock == BlockType.NOTHING ||
                            currentAllSides ||
                            (neighborBlockData?.generateAllSides == true && !blockData.generateAllSides)
                        if (!shouldRenderFace) continue

                        val shadow = getNeighborShadow(chunkData, chunkMap, nx, ny, nz, w, h)
                        MeshUtils.addChunkFace(
                            blockDataManager = blockDataManager,
                            verticesList = verticesList,
                            indicesList = indicesList,
                            x, y, z,
                            normal = dir.normal,
                            blockType = block,
                            directionType = dir.directionType,
                            shadow = shadow,
                            blockExists = { wx, wy, wz ->
                                when(getNeighborBlock(chunkData, chunkMap, wx, wy, wz, w, h)) {
                                    BlockType.AIR, BlockType.NOTHING -> false
                                    else -> true
                                }
                            }
                        )
                    }
                }
            }
        }

        val vertices = verticesList.toFloatArray()
        val indices = indicesList.toShortArray()

        return RawMeshData(vertices, indices)
    }

    private fun hasActiveAllSides(
        block: BlockType,
        blockData: TextureData,
        textureMap: Map<BlockType, TextureData>,
        chunkData: ChunkData,
        chunkMap: Map<IntVector3, ChunkData>,
        x: Int,
        y: Int,
        z: Int,
        w: Int,
        h: Int,
    ): Boolean {
        if (!blockData.generateAllSides) return false

        val worldX = chunkData.position.x * w + x
        val worldY = chunkData.position.y * h + y
        val worldZ = chunkData.position.z * w + z

        for (dir in directions) {
            val nx = x + dir.dx
            val ny = y + dir.dy
            val nz = z + dir.dz
            val neighbor = getNeighborBlock(chunkData, chunkMap, nx, ny, nz, w, h)
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

    private fun getNeighborBlock(
        currentChunk: ChunkData,
        chunkMap: Map<IntVector3, ChunkData>,
        nx: Int, ny: Int, nz: Int,
        w: Int, h: Int
    ): BlockType {
        if (nx in 0 until w && ny in 0 until h && nz in 0 until w) {
            return currentChunk.getBlockByLocal(nx, ny, nz)
        }

        var chunkOffX = 0
        var chunkOffY = 0
        var chunkOffZ = 0
        var localX = nx
        var localY = ny
        var localZ = nz

        when {
            nx < 0 -> {
                chunkOffX = -1
                localX = nx + w
            }
            nx >= w -> {
                chunkOffX = 1
                localX = nx - w
            }
        }
        when {
            ny < 0 -> {
                chunkOffY = -1
                localY = ny + h
            }
            ny >= h -> {
                chunkOffY = 1
                localY = ny - h
            }
        }
        when {
            nz < 0 -> {
                chunkOffZ = -1
                localZ = nz + w
            }
            nz >= w -> {
                chunkOffZ = 1
                localZ = nz - w
            }
        }

        val neighborChunkPos = IntVector3(
            currentChunk.position.x + chunkOffX,
            currentChunk.position.y + chunkOffY,
            currentChunk.position.z + chunkOffZ
        )
        val neighborChunk = chunkMap[neighborChunkPos]
        return if (neighborChunk != null && localY in 0 until h) {
            neighborChunk.getBlockByLocal(localX, localY, localZ)
        } else {
            BlockType.NOTHING
        }
    }

    private fun getNeighborShadow(
        currentChunk: ChunkData,
        chunkMap: Map<IntVector3, ChunkData>,
        nx: Int, ny: Int, nz: Int,
        w: Int, h: Int
    ): Float {
        if (nx in 0 until w && ny in 0 until h && nz in 0 until w) {
            return currentChunk.getDefaultShadowValue(nx, ny, nz)
        }

        var chunkOffX = 0
        var chunkOffY = 0
        var chunkOffZ = 0
        var localX = nx
        var localY = ny
        var localZ = nz

        when {
            nx < 0 -> {
                chunkOffX = -1
                localX = nx + w
            }
            nx >= w -> {
                chunkOffX = 1
                localX = nx - w
            }
        }
        when {
            ny < 0 -> {
                chunkOffY = -1
                localY = ny + h
            }
            ny >= h -> {
                chunkOffY = 1
                localY = ny - h
            }
        }
        when {
            nz < 0 -> {
                chunkOffZ = -1
                localZ = nz + w
            }
            nz >= w -> {
                chunkOffZ = 1
                localZ = nz - w
            }
        }

        val neighborChunkPos = IntVector3(
            currentChunk.position.x + chunkOffX,
            currentChunk.position.y + chunkOffY,
            currentChunk.position.z + chunkOffZ
        )
        val neighborChunk = chunkMap[neighborChunkPos]
        return if (neighborChunk != null && localY in 0 until h) {
            neighborChunk.getDefaultShadowValue(localX, localY, localZ)
        } else {
            1f
        }
    }

}