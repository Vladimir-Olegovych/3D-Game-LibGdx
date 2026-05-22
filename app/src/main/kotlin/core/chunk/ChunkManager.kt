package core.chunk

import app.feature.game.event.GameEvent
import com.artemis.World
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import core.blocks.BlockType
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.mesh.MeshData
import core.mesh.MeshHelper
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit


class ChunkManager: LaunchedEffect, DisposableEffect {

    companion object {
        const val DRAW_RADIUS_X = 32
        const val DRAW_RADIUS_Y = 2

        const val CHUNK_SIZE = 8
        const val CHUNK_HEIGHT = 64
    }
    private val parallelismMesh = Semaphore(12 * 6)

    private val chunkDataPositionToEntityId = HashMap<IntVector3, Int>()
    private val chunkMeshPositionToEntityId = HashMap<IntVector3, Int>()

    private val chunkDataMap = HashMap<IntVector3, ChunkData>()
    private val meshDataMap = HashMap<IntVector3, MeshData>()
    private var generationJob: Job? = null

    private lateinit var eventBus: EventBus
    private lateinit var meshHelper: MeshHelper
    private lateinit var terrainGenerator: TerrainGenerator
    private lateinit var defaultScope: CoroutineScope
    private lateinit var mainScope: CoroutineScope

    override fun launch(context: Context) {
        eventBus = context.getObject()
        meshHelper = context.getObject()
        terrainGenerator = context.getObject()
        defaultScope = CoroutineScope(Dispatchers.Default)
        mainScope = CoroutineScope(context.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN))
        eventBus.registerHandler(this)
    }

    override fun dispose() {
        chunkDataPositionToEntityId.clear()
        chunkMeshPositionToEntityId.clear()

        meshDataMap.clear()
        chunkDataMap.clear()

        generationJob?.cancel()
        defaultScope.cancel()
        generationJob = null
    }

    @BusEvent
    fun loadAdditionalChunks(event: GameEvent.LoadAdditionalChunksRequest) {
        tryGenerateWorld(event.world, event.playerPosition)
    }

    private fun tryGenerateWorld(world: World, playerPosition: IntVector3 = IntVector3()) {
        if(generationJob != null) return
        generationJob = worldGeneration(world, playerPosition)
    }

    private fun worldGeneration(world: World, playerPosition: IntVector3) = defaultScope.launch {
        //New world generation data by playerPosition
        val worldGenerationData = getWorldGenerationData(playerPosition)?: run {
            generationJob = null
            return@launch
        }
        //Update new data for map
        mainScope.launch {
            worldGenerationData.chunkPositionsToRemove.forEach {
                if(chunkMeshPositionToEntityId[it] != null) {
                    eventBus.sendEvent(GameEvent.OnRemoveChunkMeshData(chunkMeshPositionToEntityId[it]!!))
                    eventBus.sendEvent(GameEvent.OnRemoveChunkRigidBody(chunkMeshPositionToEntityId[it]!!))
                    meshDataMap.remove(it)
                    chunkMeshPositionToEntityId.remove(it)
                }
            }
            worldGenerationData.chunkDataToRemove.forEach {
                if(chunkDataPositionToEntityId[it] != null) {
                    eventBus.sendEvent(GameEvent.OnRemoveChunkData(chunkDataPositionToEntityId[it]!!))
                    chunkDataMap.remove(it)
                    chunkDataPositionToEntityId.remove(it)
                }
            }
        }.join()
        //Creating new entity ids for chunks
        val resultGenerationData = mainScope.async {
            val dataMap = HashMap<IntVector3, Int>()
            for(position in  worldGenerationData.chunkDataPositionsToCreate) {
                val entityId = world.create()
                dataMap[position] = entityId
                chunkDataPositionToEntityId[position] = entityId
            }
            val meshMap = HashMap<IntVector3, Int>()
            for(position in  worldGenerationData.chunkPositionsToCreate) {
                val entityId = chunkDataPositionToEntityId[position]!!
                meshMap[position] = entityId
                chunkMeshPositionToEntityId[position] = entityId
            }
            return@async Pair(dataMap, meshMap)
        }.await()
        //ChunkData jobs
        val chunkDataJobs = resultGenerationData.first.map { (position, entityId) ->
            defaultScope.async {
                generateChunkData(position, entityId)
            }
        }
        chunkDataJobs.joinAll()
        //MeshData jobs
        val fullChunkDataMap = chunkDataMap.toMap()
        val renderData = resultGenerationData.second.filter { (position, _) ->
            val ownChunkData = fullChunkDataMap[position]!!
            val data = ownChunkData.isAll(BlockType.AIR)
            if (data) {
                mainScope.launch { meshDataMap[position] = MeshData(null) }
            };!data
        }
        val meshDataJobs = renderData.map { (position, entityId) ->
            defaultScope.async {
                parallelismMesh.withPermit {
                    generateMeshData(position, entityId, fullChunkDataMap)
                }
            }
        }
        meshDataJobs.joinAll()
        //End Generation Callback
        mainScope.launch { generationJob = null }.join()
    }

    private suspend fun generateChunkData(position: IntVector3, entityId: Int) {
        val chunkData = withContext(Dispatchers.Default) {
            val data = ChunkData.create(position, CHUNK_SIZE, CHUNK_HEIGHT)
            terrainGenerator.generateChunkData(data)
            data
        }
        mainScope.launch {
            chunkDataMap[position] = chunkData
            eventBus.sendEvent(GameEvent.OnCreateChunkData(entityId, chunkData))
        }.join()
    }

    private suspend fun generateMeshData(
        position: IntVector3,
        entityId: Int,
        fullChunkDataMap: Map<IntVector3, ChunkData>
    ) {
        val ownChunkData = fullChunkDataMap[position]!!
        val rawMeshData = withContext(Dispatchers.Default) {
            meshHelper.createMesh(fullChunkDataMap, ownChunkData)
        }

        mainScope.launch {
            if (rawMeshData.isEmpty()) return@launch
            val meshData = rawMeshData.createMeshData()
            meshDataMap[position] = meshData

            eventBus.sendEvent(GameEvent.OnCreateChunkMeshData(entityId, meshData))
            eventBus.sendEvent(GameEvent.OnCreateChunkRigidBody(entityId, chunkDataMap[position]!!))
        }.join()
    }

    private fun getWorldGenerationData(playerPosition: IntVector3): WorldGenerationData? {
        val copyMesh = meshDataMap.toMap()
        val copyChunk = chunkDataMap.toMap()
        val allChunkPositionsNeeded = WorldDataHelper.getChunkPositionsAroundPlayer(playerPosition)

        val allChunkDataPositionsNeeded = WorldDataHelper.getDataPositionsAroundPlayer(playerPosition)

        val chunkPositionsToCreate = WorldDataHelper.selectPositionsToCreate(copyMesh, allChunkPositionsNeeded, playerPosition)
        val chunkDataPositionsToCreate = WorldDataHelper.selectDataPositionsToCreate(copyChunk, allChunkDataPositionsNeeded, playerPosition)

        if(chunkPositionsToCreate.isEmpty() || chunkDataPositionsToCreate.isEmpty()) {
            return null
        }

        val chunkPositionsToRemove = WorldDataHelper.getUnneededChunks(copyMesh, allChunkPositionsNeeded)
        val chunkDataToRemove = WorldDataHelper.getUnneededData(copyChunk, allChunkDataPositionsNeeded)

        val data = WorldGenerationData(
            chunkPositionsToCreate = chunkPositionsToCreate,
            chunkDataPositionsToCreate = chunkDataPositionsToCreate,
            chunkPositionsToRemove = chunkPositionsToRemove,
            chunkDataToRemove = chunkDataToRemove
        )
        return data
    }
}
