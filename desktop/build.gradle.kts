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
