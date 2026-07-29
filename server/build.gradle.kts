plugins {
    kotlin("jvm")
}

group = "com.gigcreator"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    api(files("/home/vladimir/Documents/Java/GdxUtils/build/libs/GdxUtils-1.0-SNAPSHOT.jar"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    api("net.onedaybeard.artemis:artemis-odb:2.3.0")

    api("com.badlogicgames.gdx:gdx-freetype:1.14.2")
    api("com.badlogicgames.gdx:gdx:1.14.2")
    
    api("com.esotericsoftware:kryonet:2.22.0-RC1")
    api("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    api("com.fasterxml.jackson.core:jackson-core:2.14.2")
    api("com.fasterxml.jackson.core:jackson-annotations:2.14.2")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}