import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply the Application plugin to add support for building an executable JVM application.
    application

    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    api(project(":app"))
    api("com.badlogicgames.gdx:gdx-bullet-platform:1.13.1:natives-desktop")
    api("com.badlogicgames.gdx:gdx-freetype-platform:1.13.1:natives-desktop")
    api("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-desktop")
    api("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.13.1")
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "com.gigcreator.MainKt"
}

val windowsVlcDir = layout.projectDirectory.dir("vlc/win64")
val windowsRunScript = layout.projectDirectory.file("scripts/windows/run.bat")
val windowsDistDir = layout.buildDirectory.dir("windows-dist")

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
}

tasks.register<Copy>("windowsShadowDist") {
    group = "distribution"
    description = "Build Windows distribution with shadow jar and VLC native files."
    dependsOn(shadowJarTask)

    from(shadowJarTask.map { it.archiveFile }) {
        rename(".*", "app-all.jar")
    }
    from(windowsVlcDir) {
        into("vlc")
    }
    from(windowsRunScript)

    into(windowsDistDir)
}
