package com.gigcreator.core.app

import com.gigapi.coruntines.DeltaUpdater
import com.gigapi.general.GContext
import kotlinx.coroutines.Dispatchers

class ServerApplication: DeltaUpdater(1 / 60F, Dispatchers.IO) {

    private val context = GContext()

    override fun create() {
        println("Server was created")

    }

    override fun update(deltaTime: Float) {

    }

    override fun dispose() {
        println("Server was disposed")
        context.dispose()
    }
}