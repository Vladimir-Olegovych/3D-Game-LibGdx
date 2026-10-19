package com.gigcreator

import com.gigcreator.core.app.ServerApplication
import com.gigcreator.core.congifs.ServerData
import kotlin.random.Random

fun main() {
    val serverData = ServerData(
        worldName = "Test world",
        serverName = "Dev server",
        serverPort = 5551,
        worldSeed = Random.nextInt()
    )
    val serverApplication = ServerApplication(serverData)
    serverApplication.start()
    var inProgress = true
    while (inProgress) {
        val command = readln()
        if (command == "stop") inProgress = false
    }
    serverApplication.stop(true)
}