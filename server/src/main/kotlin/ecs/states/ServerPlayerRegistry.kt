package com.gigcreator.ecs.states

import com.esotericsoftware.kryonet.Connection

internal class ServerPlayerRegistry {

    private val connectionToEntity = HashMap<Connection, Int>()
    private val entityToConnection = HashMap<Int, Connection>()

    fun addPlayer(connection: Connection, entityId: Int) {
        connectionToEntity[connection] = entityId
        entityToConnection[entityId] = connection
    }

    fun removePlayer(connection: Connection) {
        val entityId = connectionToEntity.remove(connection)
        entityId?.let { entityToConnection.remove(entityId) }
    }

    fun getEntityId(connection: Connection): Int? {
        return connectionToEntity[connection]
    }

    fun getConnection(entityId: Int): Connection? {
        return entityToConnection[entityId]
    }

}
