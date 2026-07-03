package core.chunk

import app.feature.game.event.ChunkEvent
import app.feature.game.event.EventBusTypes
import app.feature.game.event.GameEvent
import com.badlogic.gdx.math.Vector3
import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.effects.DisposableEffect
import com.gigapi.effects.LaunchedEffect
import com.gigapi.eventbus.EventBus
import com.gigapi.eventbus.annotation.BusEvent
import com.gigapi.general.Context
import com.gigapi.math.vector.IntVector3
import com.gigapi.math.vector.roundToFloat
import com.gigapi.mesh.MeshData
import core.blocks.BlockType
import core.bullet.raycast.RayCastTypes
import core.chunk.world.ChunkHelper
import core.chunk.world.WorldDataHelper
import core.chunk.world.WorldGenerationData
import core.math.createMatrixForChunk
import core.mesh.MeshHelper
import core.scope.DispatcherTypes
import core.terrain.TerrainGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ChunkWorldUpdater : LaunchedEffect, DisposableEffect, DeltaUpdater(1 / 60F, Dispatchers.Default) {

    companion object {
        const val DRAW_RADIUS_X = 16
        const val DRAW_RADIUS_Y = 12
        const val CHUNK_SIZE = 16
        const val CHUNK_HEIGHT = 16
    }

    private val workerCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    private val generationSemaphore = Semaphore(workerCount)
    private val meshSemaphore = Semaphore(workerCount)

    private val chunkDataPositionToEntityId = ConcurrentHashMap<IntVector3, Int>()
    private val chunkMeshPositionToEntityId = ConcurrentHashMap<IntVector3, Int>()

    private val chunkDataMap = ConcurrentHashMap<IntVector3, ChunkData>()
    private val meshDataMap = ConcurrentHashMap<IntVector3, MeshData>()
    private val pipelineBusy = AtomicBoolean(false)

    @Volatile
    private var pendingLoadPosition: IntVector3? = null

    private val removedChunkDates = ConcurrentHashMap.newKeySet<IntVector3>()
    private val removedChunkMeshes = ConcurrentHashMap.newKeySet<IntVector3>()

    private lateinit var mainEventBus: EventBus
    private lateinit var chunkEventBus: EventBus
    private lateinit var physicsEventBus: EventBus
    private lateinit var meshHelper: MeshHelper
    private lateinit var terrainGenerator: TerrainGenerator
    private lateinit var mainScope: CoroutineScope
    private lateinit var chunkExecutor: ExecutorCoroutineDispatcher
    private lateinit var terrainGeneratorLocal: ThreadLocal<TerrainGenerator>

    private var isFirstGeneration = true

    override fun launch(context: Context) {
        mainEventBus = context.getObject(EventBusTypes.MAIN_EVENT_BUS)
        chunkEventBus = context.getObject(EventBusTypes.CHUNK_EVENT_BUS)
        physicsEventBus = context.getObject(EventBusTypes.PHYSICS_EVENT_BUS)
        meshHelper = context.getObject()
        terrainGenerator = context.getObject()
        terrainGeneratorLocal = ThreadLocal.withInitial {
            TerrainGenerator.createWorker(terrainGenerator.worldSeed)
        }
        mainScope = CoroutineScope(context.getObject<CoroutineDispatcher>(DispatcherTypes.MAIN))
        chunkExecutor = Executors.newFixedThreadPool(workerCount).asCoroutineDispatcher()
        chunkEventBus.registerHandler(this)
        mainEventBus.registerHandler(this)
    }

    override fun create() {
    }

    override fun update(deltaTime: Float) {
        chunkEventBus.process()
    }

    override fun dispose() {
        chunkExecutor.close()
        chunkDataPositionToEntityId.clear()
        chunkMeshPositionToEntityId.clear()
        meshDataMap.clear()
        chunkDataMap.clear()
        chunkEventBus.clear()
    }

    @BusEvent
    fun loadAdditionalChunksRequest(event: ChunkEvent.LoadAdditionalChunksRequest) {
        if (pipelineBusy.get()) {
            pendingLoadPosition = event.playerPosition
            return
        }
        if (!pipelineBusy.compareAndSet(false, true)) {
            pendingLoadPosition = event.playerPosition
            return
        }
        beginLoad(event.playerPosition)
    }

    private fun beginLoad(playerPosition: IntVector3) {
        val generationData = getWorldGenerationData(playerPosition)
        val hasWork = generationData.chunkDataPositionsToCreate.isNotEmpty() ||
                generationData.chunkPositionsToCreate.isNotEmpty() ||
                generationData.chunkPositionsToRemove.isNotEmpty() ||
                generationData.chunkDataToRemove.isNotEmpty()

        if (!hasWork) {
            completeLoad()
            return
        }

        mainEventBus.sendEvent(ChunkEvent.ChunkEntitiesRequest(generationData))
        for (position in generationData.chunkDataPositionsToCreate) {
            val chunkData = ChunkData.create(position, CHUNK_SIZE, CHUNK_HEIGHT)
            chunkData.status = ChunkStatus.GENERATION
            chunkDataMap[position] = chunkData
        }
        for (position in generationData.chunkPositionsToCreate) {
            meshDataMap[position] = MeshData(null)
        }
    }

    @BusEvent
    fun chunkEntitiesResponse(event: ChunkEvent.ChunkEntitiesResponse) {
        val generationData = event.generationData

        generationData.chunkPositionsToRemove.forEach { pos ->
            removedChunkMeshes.add(pos)
            chunkMeshPositionToEntityId[pos]?.let { entityId ->
                mainEventBus.sendEvent(GameEvent.OnRemoveChunkMeshData(entityId))
                physicsEventBus.sendEvent(GameEvent.OnRemoveRigidBody(entityId))
                meshDataMap.remove(pos)
                chunkMeshPositionToEntityId.remove(pos)
            }
        }
        generationData.chunkDataToRemove.forEach { pos ->
            removedChunkDates.add(pos)
            chunkDataPositionToEntityId[pos]?.let { entityId ->
                mainEventBus.sendEvent(GameEvent.OnRemoveChunkData(entityId))
                chunkDataMap.remove(pos)
                chunkDataPositionToEntityId.remove(pos)
            }
        }

        val entities = event.entities
        for (pos in generationData.chunkDataPositionsToCreate) {
            val entityId = entities[pos] ?: continue
            chunkDataPositionToEntityId[pos] = entityId
        }
        for (pos in generationData.chunkPositionsToCreate) {
            var entityId = chunkDataPositionToEntityId[pos]
            if (entityId == null) {
                entityId = entities[pos] ?: continue
                chunkDataPositionToEntityId[pos] = entityId
            }
            chunkMeshPositionToEntityId[pos] = entityId
        }

        startChunkPipeline(generationData)
    }

    @BusEvent
    fun setBlock(event: ChunkEvent.OnSetBlock) {
        val chunkData = event.chunkData
        if (chunkData.status == ChunkStatus.GENERATION) return
        val blockPosition = event.position
        chunkData.setBlockByLocal(event.blockType, blockPosition)

        lifecycleScope.launch {
            val neighboursToUpdate = WorldDataHelper.getEdgeNeighbourChunks(chunkData, blockPosition, chunkDataMap)
            val chunksToUpdate = (listOf(chunkData.position) + neighboursToUpdate.map { it.position }).toSet()

            coroutineScope {
                chunksToUpdate.map { chunkPos ->
                    async(chunkExecutor) {
                        remeshChunk(chunkPos)
                    }
                }.awaitAll()
            }
        }
    }

    @BusEvent
    fun onRayCastResult(event: GameEvent.OnRayCastResult) {
        if (event.requestId != RayCastTypes.CHUNK_RAY_CAST || !event.hasHit) return

        val offset = event.direction.nor().scl(0.5f)
        val hitPoint = event.hitPoint.add(offset)

        val chunkPosition = WorldDataHelper.getChunkPositionFromWorldPosition(hitPoint)
        val chunkData = chunkDataMap[chunkPosition] ?: return
        val blockPosition = ChunkHelper.getBlockPositionFromWorldPosition(hitPoint)
        val currentBlock = chunkData.getBlockByLocal(blockPosition)

        if (currentBlock == BlockType.AIR) return
        chunkEventBus.sendEventNow(ChunkEvent.OnSetBlock(
            chunkData, BlockType.AIR, blockPosition
        ))
        mainEventBus.sendEvent(GameEvent.OnBlockRemoved(currentBlock, chunkPosition, blockPosition))
    }

    private fun startChunkPipeline(generationData: WorldGenerationData) {
        lifecycleScope.launch {
            try {
                coroutineScope {
                    generationData.chunkDataPositionsToCreate.map { position ->
                        async(chunkExecutor) {
                            generationSemaphore.withPermit {
                                generateChunkData(position)
                            }
                        }
                    }.awaitAll()
                }

                val affectedChunks = resolvePendingBlocks(generationData)
                if (affectedChunks.isNotEmpty()) {
                    coroutineScope {
                        affectedChunks.map { chunkPos ->
                            async(chunkExecutor) {
                                remeshChunk(chunkPos)
                            }
                        }.awaitAll()
                    }
                }

                coroutineScope {
                    generationData.chunkPositionsToCreate.map { position ->
                        async(chunkExecutor) {
                            meshSemaphore.withPermit {
                                drawChunkData(position)
                            }
                        }
                    }.awaitAll()
                }

                finishFirstGeneration(generationData.playerPosition)
            } finally {
                completeLoad()
            }
        }
    }

    private fun resolvePendingBlocks(generationData: WorldGenerationData): Set<IntVector3> {
        val affectedExistingChunks = mutableSetOf<IntVector3>()

        for ((_, chunkData) in chunkDataMap) {
            val pendingMap = chunkData.pendingBlocks
            val iterator = pendingMap.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                val pendingChunkPosition = WorldDataHelper.getChunkPositionFromBlockCoords(pending.key)
                val pendingChunkData = chunkDataMap[pendingChunkPosition] ?: continue
                val localBlockPosition = ChunkHelper.getLocalPosition(pending.key, pendingChunkPosition)
                if (pendingChunkData.setBlockByLocal(pending.value, localBlockPosition)) {
                    iterator.remove()
                    if (pendingChunkData.status != ChunkStatus.GENERATION &&
                        pendingChunkPosition !in generationData.chunkPositionsToCreate &&
                        meshDataMap[pendingChunkPosition] != null
                    ) {
                        removedChunkMeshes.add(pendingChunkPosition)
                        affectedExistingChunks.add(pendingChunkPosition)
                    }
                }
            }
        }

        return affectedExistingChunks
    }

    private fun completeLoad() {
        removedChunkDates.clear()
        removedChunkMeshes.clear()

        val next = pendingLoadPosition
        if (next != null) {
            pendingLoadPosition = null
            beginLoad(next)
            return
        }

        pipelineBusy.set(false)

        val late = pendingLoadPosition
        if (late != null && pipelineBusy.compareAndSet(false, true)) {
            pendingLoadPosition = null
            beginLoad(late)
        }
    }

    private fun areMeshNeighborsReady(position: IntVector3): Boolean {
        val chunkData = chunkDataMap[position] ?: return false
        if (chunkData.status != ChunkStatus.CREATED) return false

        for (dep in getMeshDataDependencies(position)) {
            if (dep == position) continue
            val neighbor = chunkDataMap[dep] ?: continue
            if (neighbor.status != ChunkStatus.CREATED) return false
        }
        return true
    }

    private fun getMeshDataDependencies(position: IntVector3): List<IntVector3> = listOf(
        position,
        IntVector3(position.x - 1, position.y, position.z),
        IntVector3(position.x + 1, position.y, position.z),
        IntVector3(position.x, position.y - 1, position.z),
        IntVector3(position.x, position.y + 1, position.z),
        IntVector3(position.x, position.y, position.z - 1),
        IntVector3(position.x, position.y, position.z + 1),
    )

    private fun finishFirstGeneration(playerPosition: IntVector3) {
        if (!isFirstGeneration) return

        val searchPosition = playerPosition.roundToFloat()
        val position = findSpawnPosition(searchPosition) ?: searchPosition
        mainEventBus.sendEvent(ChunkEvent.GameWorldStarted(position))
        isFirstGeneration = false
    }

    private fun findSpawnPosition(centerPosition: Vector3): Vector3? {
        val startChunkPos = WorldDataHelper.getChunkPositionFromWorldPosition(centerPosition)
        for (y in -4 + startChunkPos.y..4 + startChunkPos.y) {
            val chunkPosition = IntVector3(
                startChunkPos.x,
                y,
                startChunkPos.z
            )
            val chunkData = chunkDataMap[chunkPosition] ?: continue

            for (localX in 0 until chunkData.chunkWidth) {
                for (localZ in 0 until chunkData.chunkWidth) {
                    for (localY in 0 until chunkData.chunkHeight) {
                        val block = chunkData.getBlockByLocal(localX, localY, localZ)
                        if (block != BlockType.AIR) {
                            val aboveBlock = if (localY < chunkData.chunkHeight - 1) {
                                chunkData.getBlockByLocal(localX, localY + 1, localZ)
                            } else {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 0, localZ)
                            }

                            val aboveAboveBlock = if (localY < chunkData.chunkHeight - 2) {
                                chunkData.getBlockByLocal(localX, localY + 2, localZ)
                            } else if (localY == chunkData.chunkHeight - 2) {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 0, localZ)
                            } else {
                                val aboveChunkPos = IntVector3(chunkPosition.x, chunkPosition.y + 1, chunkPosition.z)
                                val aboveChunk = chunkDataMap[aboveChunkPos]
                                aboveChunk?.getBlockByLocal(localX, 1, localZ)
                            }

                            if (aboveBlock == BlockType.AIR && aboveAboveBlock == BlockType.AIR) {
                                val worldX = chunkPosition.x * chunkData.chunkWidth + localX
                                val worldY = chunkPosition.y * chunkData.chunkHeight + localY + 2
                                val worldZ = chunkPosition.z * chunkData.chunkWidth + localZ

                                return Vector3(worldX.toFloat(), worldY.toFloat(), worldZ.toFloat())
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    private suspend fun generateChunkData(position: IntVector3) {
        if (removedChunkDates.contains(position)) return
        val chunkData = chunkDataMap[position] ?: return
        val chunkEntityId = chunkDataPositionToEntityId[position] ?: return

        terrainGeneratorLocal.get().generateChunkData(chunkData)
        chunkData.status = ChunkStatus.CREATED

        if (removedChunkDates.contains(position)) return
        if (chunkDataPositionToEntityId[position] != chunkEntityId) return

        mainEventBus.sendEvent(
            GameEvent.OnCreateChunkTransform(
                chunkEntityId = chunkEntityId,
                transform = createMatrixForChunk(chunkData)
            )
        )
    }

    private suspend fun drawChunkData(position: IntVector3) {
        if (removedChunkDates.contains(position) || removedChunkMeshes.contains(position)) return
        val chunkData = chunkDataMap[position] ?: return
        val meshEntityId = chunkMeshPositionToEntityId[position] ?: return
        if (!areMeshNeighborsReady(position)) return

        val rawMeshData = meshHelper.createMesh(chunkDataMap, chunkData)
        if (rawMeshData.isEmpty()) return

        withContext(mainScope.coroutineContext) {
            if (removedChunkDates.contains(position) || removedChunkMeshes.contains(position)) return@withContext
            if (chunkMeshPositionToEntityId[position] != meshEntityId) return@withContext
            if (!areMeshNeighborsReady(position)) return@withContext

            val meshData = rawMeshData.createMeshData()
            meshDataMap[position] = meshData
            mainEventBus.sendEvent(GameEvent.OnCreateChunkMeshData(meshEntityId, meshData))
            physicsEventBus.sendEvent(GameEvent.OnCreateChunkRigidBody(meshEntityId, chunkData))
        }
    }

    private suspend fun remeshChunk(chunkPos: IntVector3) {
        meshSemaphore.withPermit {
            if (removedChunkDates.contains(chunkPos) || removedChunkMeshes.contains(chunkPos)) return
            val updateChunkData = chunkDataMap[chunkPos] ?: return
            if (updateChunkData.status == ChunkStatus.GENERATION) return
            val updateChunkEntityId = chunkDataPositionToEntityId[chunkPos] ?: return
            if (!areMeshNeighborsReady(chunkPos)) return

            val rawMeshData = meshHelper.createMesh(chunkDataMap, updateChunkData)
            withContext(mainScope.coroutineContext) {
                if (removedChunkDates.contains(chunkPos) || removedChunkMeshes.contains(chunkPos)) return@withContext
                if (chunkDataPositionToEntityId[chunkPos] != updateChunkEntityId) return@withContext

                val meshData = rawMeshData.createMeshData()
                meshDataMap[chunkPos] = meshData
                physicsEventBus.sendEvent(GameEvent.OnUpdateChunkData(updateChunkEntityId, updateChunkData))
                mainEventBus.sendEvent(GameEvent.OnUpdateChunkMeshData(updateChunkEntityId, meshData))
            }
        }
    }

    private fun getWorldGenerationData(playerPosition: IntVector3): WorldGenerationData {
        val allChunkPositions = WorldDataHelper.getChunkPositionsAroundPlayer(playerPosition)
        val allDataPositions = WorldDataHelper.getDataPositionsAroundPlayer(playerPosition)

        val chunkPositionsToCreate = WorldDataHelper.selectPositionsToCreate(meshDataMap, allChunkPositions, playerPosition)
        val dataPositionsToCreate = WorldDataHelper.selectDataPositionsToCreate(chunkDataMap, allDataPositions, playerPosition)
        val chunkPositionsToRemove = WorldDataHelper.getUnneededChunks(meshDataMap, allChunkPositions)
        val dataToRemove = WorldDataHelper.getUnneededData(chunkDataMap, allDataPositions)

        return WorldGenerationData(
            playerPosition = playerPosition,
            chunkPositionsToCreate = chunkPositionsToCreate,
            chunkDataPositionsToCreate = dataPositionsToCreate,
            chunkPositionsToRemove = chunkPositionsToRemove,
            chunkDataToRemove = dataToRemove
        )
    }
}
