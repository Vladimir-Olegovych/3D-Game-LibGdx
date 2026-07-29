package com.gigcreator

import com.gigcreator.core.app.ServerApplication

fun main() {
    val serverApplication = ServerApplication()
    serverApplication.start()
    var inProgress = true
    while (inProgress) {
        val command = readln()
        if (command == "stop") inProgress = false
    }
    serverApplication.stop(true)
}