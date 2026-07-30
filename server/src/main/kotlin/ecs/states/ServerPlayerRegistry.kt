package com.gigcreator.ecs.states

import com.esotericsoftware.kryonet.Connection

class ServerPlayerRegistry {
    val connectionToEntity = HashMap<Int, Int>()

    fun getEntityId(connection: Connection): Int? = connectionToEntity[connection.id]
}