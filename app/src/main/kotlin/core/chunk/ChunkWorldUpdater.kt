package core.chunk

import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameChunkEvent
import app.feature.game.event.PhysicsEvent
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.GContext
import com.gigapi.math.vector.IntVector3
import com.gigapi.math.vector.roundToFloat
import com.gigapi.mesh.MeshData
import core.chunk.world.WorldChunksPreparation
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.math.createMatrixForChunk
import core.mesh.MeshGenerator
import core.mesh.MeshHelper
import core.mesh.chunkMeshParams
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import core.terrain.biome.models.BiomeType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ChunkWorldUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    companion object {
        const val DRAW_RADIUS_X = 32
        const val DRAW_RADIUS_Y = 12
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 16
        const val DATA_RADIUS_EXTRA = 1
        const val REMOVE_MARGIN = 1
    }

    private val isFirstGeneration = AtomicBoolean(true)

    private val workerCount = (Runtime.getRuntime().availableProcessors() / 6).coerceAtLeast(2)

    private val parallelismChunks = Semaphore(workerCount)
    private val parallelismMesh = Semaphore(workerCount)
    private val chunkPoolUpdater = ChunkPoolUpdater()

    private val pendingMeshPositions = ConcurrentHashMap.newKeySet<IntVector3>()
    private val pendingChunkPositions = ConcurrentHashMap.newKeySet<IntVector3>()

    private val chunkDataMap = ConcurrentHashMap<IntVector3, ChunkData>()
    private val meshDataMap = ConcurrentHashMap<IntVector3, MeshData>()

    private var chunkEventBus: EventBus? = null
    private lateinit var mainEventBus: EventBus
    private lateinit var physicsEventBus: EventBus

    private lateinit var meshGenerator: MeshGenerator
    private lateinit var terrainGenerator: TerrainGenerator
    private lateinit var mainScope: CoroutineScope

    override fun launch(gContext: GContext) {
        chunkEventBus = gContext.getObject(EventBusTypes.CHUNK_EVENT_BUS)
        physicsEventBus = gContext.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        meshGenerator = gContext.getObject<MeshHelper>()
        terrainGenerator = gContext.getObject()
        mainEventBus = gContext.getObject(EventBusTypes.MAIN_EVENT_BUS)
        mainScope = CoroutineScope(
            gContext.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN)
        )

        chunkEventBus?.registerHandler(this)
    }

    override fun dispose() {
        chunkDataMap.clear()
        meshDataMap.clear()
        pendingMeshPositions.clear()
        pendingChunkPositions.clear()
        chunkPoolUpdater.clear()
        chunkEventBus?.clear()
    }

    override fun create() = Unit

    override fun update(deltaTime: Float) {
        chunkEventBus?.process()
    }

    @BusEvent
    fun loadChunksRequest(event: ChunkEvent.LoadAdditionalChunksRequest) {
        val generationData = getWorldGenerationData(event.playerPosition)
        chunkEventBus?.sendEvent(ChunkEvent.ChunksPreparation(generationData))
    }

    @BusEvent
    fun chunksPreparation(event: ChunkEvent.ChunksPreparation) {
        val generationData = event.worldGenerationData

        val meshPositionsToCreate = mutableListOf<IntVector3>()
        val chunkPositionsToCreate = mutableListOf<IntVector3>()

        for (position in generationData.allMeshDataPositionsAround) {
            if (meshDataMap.containsKey(position)) continue
            if (!pendingMeshPositions.add(position)) continue

            meshDataMap[position] = MeshData(null)
            meshPositionsToCreate.add(position)
        }

        for (position in generationData.allChunkDataPositionsAround) {
            if (chunkDataMap.containsKey(position)) continue
            if (!pendingChunkPositions.add(position)) continue

            chunkPositionsToCreate.add(position)
        }

        removeOutsideChunks(generationData.playerPosition)

        WorldDataHelper.sortChunksByDistance(
            chunks = chunkPositionsToCreate,
            playerPosition = generationData.playerPosition
        )

        WorldDataHelper.sortChunksByDistance(
            chunks = meshPositionsToCreate,
            playerPosition = generationData.playerPosition
        )

        val preparation = WorldChunksPreparation(
            playerPosition = generationData.playerPosition,
            toCreateMeshDates = meshPositionsToCreate,
            toCreateChunkDates = chunkPositionsToCreate
        )

        mainEventBus.sendEvent(
            GameChunkEvent.OnRequireChunkEntityIds(preparation)
        )
    }

    @BusEvent
    fun chunksGenerating(event: ChunkEvent.ChunksGenerating) = lifecycleScope.launch(Dispatchers.Default) {
        val preparation = event.worldChunksPreparation
        val createdChunks = HashMap<IntVector3, ChunkData>()

        val points = terrainGenerator.generateBiomePoints(preparation.playerPosition)

        for (position in preparation.toCreateChunkDates) {
            val entityId = event.entityIds[position] ?: continue

            val chunkData = chunkPoolUpdater.createChunk(position)
            chunkData.entityId = entityId

            createdChunks[position] = chunkData
        }

        preparation.toCreateChunkDates.map { position ->
            async(Dispatchers.Default) {
                val chunkData = createdChunks[position] ?: return@async

                parallelismChunks.withPermit {
                    generateChunk(chunkData, points)
                }
            }
        }.awaitAll()


        chunkDataMap.putAll(createdChunks)
        pendingChunkPositions.removeAll(createdChunks.keys)

        chunkEventBus?.sendEvent(
            ChunkEvent.ChunksMeshing(preparation)
        )
    }

    @BusEvent
    fun chunksMeshing(event: ChunkEvent.ChunksMeshing) = lifecycleScope.launch(Dispatchers.Default) {
        val preparation = event.worldChunksPreparation

        preparation.toCreateMeshDates.map { position ->
            async(Dispatchers.Default) {
                parallelismMesh.withPermit {
                    meshChunk(position)
                }
            }
        }.awaitAll()

        pendingMeshPositions.removeAll(preparation.toCreateMeshDates.toSet())

        chunkEventBus?.sendEvent(
            ChunkEvent.FinalizeCreation(preparation)
        )
    }

    @BusEvent
    fun finalizeCreation(event: ChunkEvent.FinalizeCreation) {
        if (!isFirstGeneration.compareAndSet(true, false)) return

        val searchPosition = event.worldChunksPreparation.playerPosition.roundToFloat()

        mainEventBus.sendEvent(
            ChunkEvent.GameWorldStarted(searchPosition)
        )
    }

    private fun removeOutsideChunks(playerPosition: IntVector3) {
        val playerChunk = WorldDataHelper.chunkPositionFromBlockCoords(playerPosition)

        val meshRemoveRadiusX = DRAW_RADIUS_X + REMOVE_MARGIN
        val meshRemoveRadiusY = DRAW_RADIUS_Y + REMOVE_MARGIN

        val dataRemoveRadiusX = DRAW_RADIUS_X + DATA_RADIUS_EXTRA + REMOVE_MARGIN
        val dataRemoveRadiusY = DRAW_RADIUS_Y + DATA_RADIUS_EXTRA + REMOVE_MARGIN

        val meshesToRemove = meshDataMap.keys.filter { position ->
            WorldDataHelper.isOutsideRadius(
                position = position,
                center = playerChunk,
                radiusXZ = meshRemoveRadiusX,
                radiusY = meshRemoveRadiusY
            )
        }

        val chunksToRemove = chunkDataMap.keys.filter { position ->
            WorldDataHelper.isOutsideRadius(
                position = position,
                center = playerChunk,
                radiusXZ = dataRemoveRadiusX,
                radiusY = dataRemoveRadiusY
            )
        }

        for (position in meshesToRemove) {
            val meshData = meshDataMap.remove(position) ?: continue
            val chunkData = chunkDataMap[position]
            val entityId = chunkData?.entityId

            if (entityId != null) {
                mainEventBus.sendEvent(
                    GameChunkEvent.OnRemoveChunkMeshData(entityId)
                )
                physicsEventBus.sendEvent(
                    PhysicsEvent.OnRemoveRigidBody(entityId)
                )
            }

            pendingMeshPositions.remove(position)
        }

        for (position in chunksToRemove) {
            val chunkData = chunkDataMap.remove(position) ?: continue
            val entityId = chunkData.entityId

            if (entityId != null) {
                mainEventBus.sendEvent(
                    GameChunkEvent.OnRemoveChunkData(entityId)
                )
            }

            chunkPoolUpdater.removeChunk(chunkData)
            pendingChunkPositions.remove(position)
        }
    }

    private fun generateChunk(chunkData: ChunkData,
                              points: Map<IntVector3, BiomeType>) {
        val entityId = chunkData.entityId ?: return

        terrainGenerator.generateChunkData(chunkData, points)

        mainEventBus.sendEvent(
            GameChunkEvent.OnCreateChunkTransform(
                chunkEntityId = entityId,
                transform = createMatrixForChunk(chunkData)
            )
        )
    }

    private suspend fun meshChunk(position: IntVector3) {
        val chunkData = chunkDataMap[position] ?: return
        val entityId = chunkData.entityId ?: return

        if (!hasRequiredNeighbors(position)) {
            // Do not create a mesh yet: wait until all horizontal neighbors exist.
            return
        }

        val rawMeshData = meshGenerator.createMesh(chunkDataMap, chunkData)

        if (rawMeshData.isEmpty()) {
            meshDataMap.remove(position)
            return
        }

        val meshData = withContext(mainScope.coroutineContext) {
            rawMeshData.createMeshData(chunkMeshParams)
        }

        // The chunk could have been removed while its mesh was being created.
        val currentChunkData = chunkDataMap[position] ?: return
        val currentEntityId = currentChunkData.entityId ?: return

        if (currentEntityId != entityId) return

        meshDataMap[position] = meshData

        mainEventBus.sendEvent(
            GameChunkEvent.OnCreateChunkMeshData(currentEntityId, meshData)
        )

        physicsEventBus.sendEvent(
            PhysicsEvent.OnCreateChunkRigidBody(currentEntityId, currentChunkData)
        )
    }

    private fun hasRequiredNeighbors(position: IntVector3): Boolean {
        val north = IntVector3(position.x, position.y, position.z - 1)
        val south = IntVector3(position.x, position.y, position.z + 1)
        val west = IntVector3(position.x - 1, position.y, position.z)
        val east = IntVector3(position.x + 1, position.y, position.z)

        return chunkDataMap.containsKey(north) &&
                chunkDataMap.containsKey(south) &&
                chunkDataMap.containsKey(west) &&
                chunkDataMap.containsKey(east)
    }

    private fun getWorldGenerationData(
        playerPosition: IntVector3
    ): WorldGenerationData {
        val meshPositions =
            WorldDataHelper.getChunkPositionsAroundPlayer(playerPosition)

        val dataPositions =
            WorldDataHelper.getDataPositionsAroundPlayer(playerPosition)

        return WorldGenerationData(
            playerPosition = playerPosition,
            allChunkDataPositionsAround = dataPositions,
            allMeshDataPositionsAround = meshPositions
        )
    }
}