package core.chunk

import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.artemis.World
import com.gigapi.core.effects.DisposableEffect
import com.gigapi.core.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import com.gigapi.screens.mesh.MeshData
import core.blocks.BlockType
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.mesh.MeshHelper
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

class ChunkManager : LaunchedEffect, DisposableEffect {

    companion object {
        const val DRAW_RADIUS_X = 16
        const val DRAW_RADIUS_Y = 12
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 16
    }

    private val parallelismMesh = Semaphore(DRAW_RADIUS_X * 6 * 4)

    private val chunkDataPositionToEntityId = ConcurrentHashMap<IntVector3, Int>()
    private val chunkMeshPositionToEntityId = ConcurrentHashMap<IntVector3, Int>()
    private val chunkDataMap = ConcurrentHashMap<IntVector3, ChunkData>()
    private val meshDataMap = ConcurrentHashMap<IntVector3, MeshData>()

    private val pendingChunks = ConcurrentHashMap.newKeySet<IntVector3>()

    private lateinit var mainEventBus: EventBus
    private lateinit var physicsEventBus: EventBus
    private lateinit var meshHelper: MeshHelper
    private lateinit var terrainGenerator: TerrainGenerator
    private lateinit var defaultScope: CoroutineScope
    private lateinit var mainScope: CoroutineScope

    override fun launch(context: Context) {
        mainEventBus = context.getObject(EventBusTypes.MAIN_EVENT_BUS)
        physicsEventBus = context.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        meshHelper = context.getObject()
        terrainGenerator = context.getObject()
        defaultScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        mainScope = CoroutineScope(context.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN))
        mainEventBus.registerHandler(this)
    }

    override fun dispose() {
        defaultScope.cancel()
        chunkDataPositionToEntityId.clear()
        chunkMeshPositionToEntityId.clear()
        meshDataMap.clear()
        chunkDataMap.clear()
        pendingChunks.clear()
    }

    @BusEvent
    fun loadAdditionalChunks(event: GameEvent.LoadAdditionalChunksRequest) {
        defaultScope.launch {
            performWorldGeneration(event.world, event.playerPosition)
        }
    }

    private suspend fun performWorldGeneration(world: World, playerPosition: IntVector3) {
        val generationData = getWorldGenerationData(playerPosition) ?: return

        withContext(mainScope.coroutineContext) {
            generationData.chunkPositionsToRemove.forEach { pos ->
                chunkMeshPositionToEntityId[pos]?.let { entityId ->
                    mainEventBus.sendEvent(GameEvent.OnRemoveChunkMeshData(entityId))
                    physicsEventBus.sendEvent(GameEvent.OnRemoveRigidBody(entityId))
                    meshDataMap.remove(pos)
                    chunkMeshPositionToEntityId.remove(pos)
                }
            }
            generationData.chunkDataToRemove.forEach { pos ->
                chunkDataPositionToEntityId[pos]?.let { entityId ->
                    mainEventBus.sendEvent(GameEvent.OnRemoveChunkData(entityId))
                    chunkDataMap.remove(pos)
                    chunkDataPositionToEntityId.remove(pos)
                }
                pendingChunks.remove(pos)
            }
        }

        val dataIdMap = ConcurrentHashMap<IntVector3, Int>()
        val meshIdMap = ConcurrentHashMap<IntVector3, Int>()
        withContext(mainScope.coroutineContext) {
            for (pos in generationData.chunkDataPositionsToCreate) {
                val entityId = world.create()
                dataIdMap[pos] = entityId
                chunkDataPositionToEntityId[pos] = entityId
            }
            for (pos in generationData.chunkPositionsToCreate) {
                var entityId = chunkDataPositionToEntityId[pos]
                if (entityId == null) {
                    entityId = world.create()
                    chunkDataPositionToEntityId[pos] = entityId
                    dataIdMap[pos] = entityId
                }
                meshIdMap[pos] = entityId
                chunkMeshPositionToEntityId[pos] = entityId
            }
        }

        val dataJobs = dataIdMap.map { (pos, entityId) ->
            defaultScope.async { generateChunkData(pos, entityId) }
        }
        dataJobs.joinAll()

        val fullDataMap = chunkDataMap.toMap()
        val renderablePositions = meshIdMap.filter { (pos, _) ->
            val chunk = fullDataMap[pos] ?: return@filter false
            val isAir = chunk.isAllBlock(BlockType.AIR)
            if (isAir) {
                mainScope.launch { meshDataMap[pos] = MeshData(null) }
            }; !isAir
        }
        val meshJobs = renderablePositions.map { (pos, entityId) ->
            defaultScope.async {
                parallelismMesh.withPermit {
                    generateMeshData(pos, entityId, fullDataMap)
                }
            }
        }
        meshJobs.joinAll()

        pendingChunks.removeAll(dataIdMap.keys)
    }

    private suspend fun generateChunkData(position: IntVector3, entityId: Int) {
        val chunkData = withContext(Dispatchers.Default) {
            ChunkData.create(position, CHUNK_SIZE, CHUNK_HEIGHT).also {
                terrainGenerator.generateChunkData(it)
            }
        }
        withContext(mainScope.coroutineContext) {
            chunkDataMap[position] = chunkData
            mainEventBus.sendEvent(GameEvent.OnCreateChunkData(entityId, chunkData))
        }
    }

    private suspend fun generateMeshData(
        position: IntVector3,
        entityId: Int,
        fullDataMap: Map<IntVector3, ChunkData>
    ) {
        val ownChunkData = fullDataMap[position] ?: return
        val rawMeshData = withContext(Dispatchers.Default) {
            meshHelper.createMesh(fullDataMap, ownChunkData)
        }
        withContext(mainScope.coroutineContext) {
            if (rawMeshData.isEmpty()) return@withContext
            val meshData = rawMeshData.createMeshData()
            meshDataMap[position] = meshData
            mainEventBus.sendEvent(GameEvent.OnCreateChunkMeshData(entityId, meshData))
            physicsEventBus.sendEvent(GameEvent.OnCreateChunkRigidBody(entityId, chunkDataMap[position]!!))
        }
    }

    private fun getWorldGenerationData(playerPosition: IntVector3): WorldGenerationData? {
        val allChunkPositions = WorldDataHelper.getChunkPositionsAroundPlayer(playerPosition)
        val allDataPositions = WorldDataHelper.getDataPositionsAroundPlayer(playerPosition)

        val chunkPositionsToCreate = WorldDataHelper.selectPositionsToCreate(meshDataMap, allChunkPositions, playerPosition)
            .filter { it !in pendingChunks }
        val dataPositionsToCreate = WorldDataHelper.selectDataPositionsToCreate(chunkDataMap, allDataPositions, playerPosition)
            .filter { it !in pendingChunks }

        if (chunkPositionsToCreate.isEmpty() || dataPositionsToCreate.isEmpty()) {
            return null
        }

        pendingChunks.addAll(dataPositionsToCreate)

        val chunkPositionsToRemove = WorldDataHelper.getUnneededChunks(meshDataMap, allChunkPositions)
        val dataToRemove = WorldDataHelper.getUnneededData(chunkDataMap, allDataPositions)

        return WorldGenerationData(
            chunkPositionsToCreate = chunkPositionsToCreate,
            chunkDataPositionsToCreate = dataPositionsToCreate,
            chunkPositionsToRemove = chunkPositionsToRemove,
            chunkDataToRemove = dataToRemove
        )
    }
}