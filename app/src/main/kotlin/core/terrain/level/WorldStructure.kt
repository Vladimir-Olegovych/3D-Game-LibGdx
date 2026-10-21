package core.terrain.level

import core.terrain.biome.models.BiomeType
import core.terrain.structures.builder.StructureBuilder

/**
 * Структура произвольного размера. Генерируется по мировым координатам, не зависит от чанков.
 *
 * ПРАВИЛО: build() должен принимать решения ТОЛЬКО через builder.random и параметры.
 * Нельзя ветвиться по тому, что реально записалось (клиппинг по чанку этого не покажет).
 */
abstract class WorldStructure {

    /** Уникальное имя: подмешивается в хэш, чтобы разные структуры не совпадали позициями. */
    abstract val id: String

    /** Размер ячейки сетки (в блоках): одна попытка спавна на ячейку. Задаёт плотность и минимальный разброс. */
    abstract val cellSize: Int

    /** Шанс спавна в ячейке 0..1. */
    open val chance: Float = 1f

    /** Максимальный вылет по X/Z от точки привязки (с запасом!). */
    abstract val radius: Int

    /** Насколько структура уходит ВНИЗ от поверхности (подвалы, колодцы). */
    open val below: Int = 0

    /** Насколько структура уходит ВВЕРХ от блока поверхности (с запасом!). */
    abstract val above: Int

    /** Допустимые биомы, null = любые. */
    open val biomes: Set<BiomeType>? = null

    /** Дополнительное условие по условиям мира в точке привязки. */
    open fun canSpawn(surfaceY: Int, biome: BiomeType): Boolean = true

    /** Строит структуру. Точка (0,0,0) билдера = блок поверхности в точке привязки. */
    abstract fun build(b: StructureBuilder)

    open fun buildClipped(
        b: StructureBuilder,
        minX: Int, maxX: Int,
        minY: Int, maxY: Int,
        minZ: Int, maxZ: Int
    ) {
        build(b)
    }
}