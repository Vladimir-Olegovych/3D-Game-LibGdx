plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

dependencies {
    api(files("/home/vladimir/Documents/Java/GdxUtils/build/libs/GdxUtils-1.0-SNAPSHOT.jar"))
    api(files("/home/vladimir/Documents/Java/GdxGraphicsUtils/build/libs/GdxGraphicsUtils-1.0-SNAPSHOT.jar"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    api("net.onedaybeard.artemis:artemis-odb:2.3.0")

    api("com.badlogicgames.gdx:gdx-freetype:1.13.1")
    api("com.badlogicgames.gdx:gdx:1.13.1")
    api("com.badlogicgames.gdx:gdx-bullet:1.13.1")

    api("de.javagl:obj:0.4.0")
    api("com.esotericsoftware:kryo:5.5.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    api("com.fasterxml.jackson.core:jackson-core:2.14.2")
    api("com.fasterxml.jackson.core:jackson-annotations:2.14.2")
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "com.gigcreator.app.AppKt"
}
