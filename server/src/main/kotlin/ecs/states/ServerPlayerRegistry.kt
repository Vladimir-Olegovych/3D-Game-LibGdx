package com.gigcreator.ecs.states

import com.gigcreator.NetQuaternion
import com.gigcreator.NetVector3

class ServerPlayerRegistry {

    data class PlayerNetworkState(
        val pos: NetVector3 = NetVector3.ZERO,
        val rot: NetQuaternion = NetQuaternion.identity(),
        val modelId: Int = 0,
    )

    val connectionToEntity = HashMap<Int, Int>()
    private val entityToPlayerId = HashMap<Int, Int>()
    private val playerIdToEntity = HashMap<Int, Int>()
    val playerStates = HashMap<Int, PlayerNetworkState>()
    private var nextPlayerId = 1

    fun assignPlayer(connectionId: Int, entityId: Int): Int {
        val playerId = nextPlayerId++
        connectionToEntity[connectionId] = entityId
        entityToPlayerId[entityId] = playerId
        playerIdToEntity[playerId] = entityId
        playerStates[playerId] = PlayerNetworkState()
        return playerId
    }

    fun removeByConnection(connectionId: Int): Pair<Int, Int>? {
        val entityId = connectionToEntity.remove(connectionId) ?: return null
        val playerId = entityToPlayerId.remove(entityId)
        if (playerId != null) {
            playerIdToEntity.remove(playerId)
            playerStates.remove(playerId)
        }
        return if (playerId != null) playerId to entityId else null
    }

    fun getPlayerIdByConnection(connectionId: Int): Int? {
        val entityId = connectionToEntity[connectionId] ?: return null
        return entityToPlayerId[entityId]
    }

    fun updateState(playerId: Int, pos: NetVector3, rot: NetQuaternion, modelId: Int) {
        playerStates[playerId] = PlayerNetworkState(pos, rot, modelId)
    }
}
