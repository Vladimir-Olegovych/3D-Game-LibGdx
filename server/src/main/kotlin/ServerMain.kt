package com.gigcreator

import com.gigcreator.core.app.ServerApplication
import com.gigcreator.core.congifs.ServerData

fun main() {
    val serverData = ServerData(
        worldName = "Test world",
        serverName = "Dev server",
        worldSeed = 100
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