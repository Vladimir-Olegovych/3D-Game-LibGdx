package core.terrain.level


abstract class StructureGenerator() {
    abstract fun onGetStructure(): ByteArray
}