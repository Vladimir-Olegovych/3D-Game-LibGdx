package com.gigcreator

import app.GameApplication
import com.badlogic.gdx.Game
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import java.io.File
import java.nio.file.Paths

fun main() {
    configureWindowsVlc()
    val gameApplication = GameApplication()
    startScreen(gameApplication)
}

private fun startScreen(game: Game){
    val config = Lwjgl3ApplicationConfiguration()
    config.useVsync(true)
    //config.setWindowSizeLimits(1000, 700, 1000, 700)
    config.setForegroundFPS((Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate * 2))
    config.setIdleFPS(30)
    config.setTitle("Amogus Craft")
    //config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
    Lwjgl3Application(game, config)
}

private fun configureWindowsVlc() {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    if (!osName.contains("win")) return

    val vlcDir = locateWindowsVlcDir() ?: return

    val currentPath = System.getProperty("jna.library.path").orEmpty().trim()
    val jnaPath = if (currentPath.isEmpty()) {
        vlcDir.absolutePath
    } else {
        "$currentPath${File.pathSeparator}${vlcDir.absolutePath}"
    }

    val pluginsDir = vlcDir.resolve("plugins")
    if (pluginsDir.isDirectory) {
        System.setProperty("VLC_PLUGIN_PATH", pluginsDir.absolutePath)
    }

    System.setProperty("jna.library.path", jnaPath)
    NativeLibrary.addSearchPath("libvlccore", vlcDir.absolutePath)
    NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), vlcDir.absolutePath)

    // Preload core libraries by absolute path so Windows can resolve dependent DLLs.
    runCatching { System.load(vlcDir.resolve("libvlccore.dll").absolutePath) }
    runCatching { System.load(vlcDir.resolve("libvlc.dll").absolutePath) }
}

private fun locateWindowsVlcDir(): File? {
    val candidates = linkedSetOf<File>()

    val appLocation = runCatching {
        Paths.get(object {}.javaClass.protectionDomain.codeSource.location.toURI()).toFile()
    }.getOrNull()

    if (appLocation != null) {
        val appDir = if (appLocation.isFile) appLocation.parentFile else appLocation
        if (appDir != null) {
            candidates.add(appDir.resolve("vlc"))
        }
    }

    candidates.add(Paths.get("vlc").toAbsolutePath().normalize().toFile())

    return candidates.firstOrNull { dir ->
        dir.resolve("libvlc.dll").isFile && dir.resolve("libvlccore.dll").isFile
    }
}
