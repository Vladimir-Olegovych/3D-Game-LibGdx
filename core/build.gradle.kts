plugins {
    kotlin("jvm")
}

group = "com.gigcreator"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api("com.esotericsoftware:kryo:5.5.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}