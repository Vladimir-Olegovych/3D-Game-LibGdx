package core.terrain.structures

import core.blocks.BlockType
import core.terrain.TerrainGenerator.Companion.SURFACE_HEIGHT
import core.terrain.biome.models.BiomeType
import core.terrain.level.WorldStructure
import core.terrain.structures.builder.StructureBuilder
import kotlin.math.abs

/**
 * Не «коробка», а замок:
 *  - куртина (стена) с зубцами снаружи, поручнем изнутри, бойницами и настилом-коридором сверху;
 *  - 4 круглые угловые башни;
 *  - башни по центрам стен, к ним ведут деревянные МОСТЫ-переходы от донжона;
 *  - ворота: арка, деревянный порткуллис, настил над проездом, фасад между башнями;
 *  - донжон: 3 этажа-зала, лестничный колодец вдоль стены, столбы, перегородка с дверью,
 *    арочные окна с деревянными наличниками;
 *  - хозяйственные постройки во дворе и мощёные дорожки.
 *
 * Внутренний двор НЕ заливается блоками — там остаётся естественный рельеф.
 */
class OldHouseStructure : WorldStructure() {

    override val id: String = "old_house" // можно переименовать в "castle"

    // --- Габариты (симметрия вокруг 0) ---
    private val halfW = 44          // куртина: |x| <= 44
    private val halfD = 44          // куртина: |z| <= 44
    private val wallH = 12          // высота куртины: камень 0..11, настил 12
    private val cornerTowerH = 20   // угловые башни: настил на 20, зубцы 21..22
    private val keepHalf = 10       // донжон 21x21, стены толщиной 2
    private val keepH = 27          // уровень крыши-настила донжона

    // --- Материалы (если в BlockType есть COBBLESTONE/STONE_BRICKS/GLASS — замените для красоты) ---
    private val stone = BlockType.STONE
    private val plank = BlockType.OAK_WOODEN_PLANKS
    private val wood = BlockType.OAK_WOOD

    override val cellSize: Int = 8
    override val chance: Float = 0.0002f
    override val biomes = setOf(BiomeType.FOREST, BiomeType.SPRUCE_FOREST)

    override val radius: Int = halfW + 7   // угловые башни выступают до halfW + 5
    override val above: Int = 33           // самое высокое — финал башенок донжона (y = 31)

    override fun build(b: StructureBuilder) {
        buildClipped(b, -(halfW + 6), halfW + 6, 0, above - 1, -(halfD + 6), halfD + 6)
    }

    private enum class Kind {
        CORNER_TOWER, GATE_TOWER, MID_TOWER, GATE, CURTAIN, KEEP, BRIDGE, HOUSE, PATH, EMPTY
    }

    override fun canSpawn(surfaceY: Int, biome: BiomeType): Boolean {
        return surfaceY < SURFACE_HEIGHT && super.canSpawn(surfaceY, biome)
    }

    override fun buildClipped(
        b: StructureBuilder,
        minX: Int, maxX: Int,
        minY: Int, maxY: Int,
        minZ: Int, maxZ: Int
    ) {
        val x0 = maxOf(-(halfW + 6), minX)
        val x1 = minOf(halfW + 6, maxX)
        val y0 = maxOf(0, minY)
        val y1 = minOf(above - 1, maxY)
        val z0 = maxOf(-(halfD + 6), minZ)
        val z1 = minOf(halfD + 6, maxZ)
        if (x0 > x1 || y0 > y1 || z0 > z1) return

        for (x in x0..x1) {
            for (z in z0..z1) {
                val kind = kindOf(x, z)
                if (kind == Kind.EMPTY) continue
                for (y in y0..y1) {
                    val block = blockAt(x, y, z, kind) ?: continue
                    b.set(x, y, z, block) // AIR тоже ставим — пробиваем проёмы
                }
            }
        }
    }

    // ==================== КЛАССИФИКАЦИЯ КОЛОННЫ (x, z) ====================

    private fun kindOf(x: Int, z: Int): Kind = when {
        inCornerTower(x, z) -> Kind.CORNER_TOWER
        abs(x) <= 2 && z in (halfD - 1)..(halfD + 3) -> Kind.GATE
        z in (halfD - 3)..(halfD + 3) && abs(x) in 3..9 -> Kind.GATE_TOWER
        (abs(x) >= halfW - 3 && abs(z) <= 3) || (abs(x) <= 3 && z <= -(halfD - 3)) -> Kind.MID_TOWER
        isCurtain(x, z) -> Kind.CURTAIN
        abs(x) <= keepHalf && abs(z) <= keepHalf -> Kind.KEEP
        bridgeAxis(x, z) >= 0 -> Kind.BRIDGE
        abs(x) in 20..32 && abs(z) in 21..31 -> Kind.HOUSE
        isPath(x, z) -> Kind.PATH
        else -> Kind.EMPTY
    }

    private fun inCornerTower(x: Int, z: Int): Boolean {
        val dx = abs(x) - halfW
        val dz = abs(z) - halfD
        return dx * dx + dz * dz <= 30      // «круглая» башня радиусом ~5.5
    }

    private fun isCurtain(x: Int, z: Int): Boolean =
        (abs(x) >= halfW - 1 && abs(z) <= halfD) || (abs(z) >= halfD - 1 && abs(x) <= halfW)

    /** 0 — мосты вдоль X (восток/запад), 1 — мост на север, -1 — не мост. */
    private fun bridgeAxis(x: Int, z: Int): Int = when {
        abs(z) <= 1 && abs(x) in 11..(halfW - 4) -> 0
        abs(x) <= 1 && z in -(halfD - 4)..-11 -> 1
        else -> -1
    }

    private fun isPath(x: Int, z: Int): Boolean =
        (abs(x) <= 1 && z in (keepHalf + 1)..(halfD - 2)) ||
                (abs(z) <= 1 && abs(x) in (keepHalf + 1)..(halfW - 4))

    // ==================== БЛОК ПО КООРДИНАТАМ ====================

    private fun blockAt(x: Int, y: Int, z: Int, kind: Kind): BlockType? = when (kind) {
        Kind.CORNER_TOWER -> cornerTowerBlock(x, y, z)
        Kind.GATE_TOWER, Kind.MID_TOWER -> towerBlock(x, y, z)
        Kind.GATE -> gateBlock(x, y, z)
        Kind.CURTAIN -> curtainBlock(x, y, z)
        Kind.KEEP -> keepBlock(x, y, z)
        Kind.BRIDGE -> bridgeBlock(x, y, z)
        Kind.HOUSE -> houseBlock(x, y, z)
        Kind.PATH -> if (y == 0) plank else null
        Kind.EMPTY -> null
    }

    // ---------- Куртина: зубцы снаружи, поручень изнутри, бойницы, настил-коридор ----------

    private fun curtainBlock(x: Int, y: Int, z: Int): BlockType? {
        val outer = abs(x) == halfW || abs(z) == halfD
        return when {
            y == 0 -> stone
            y in 1..wallH - 1 -> if (curtainSlit(x, z, y)) BlockType.AIR else stone
            y == wallH -> plank                                        // крепостной «коридор»
            y == wallH + 1 -> stone                                    // низкий поручень по всему периметру
            y == wallH + 2 -> if (outer && crenel(x, z)) stone else null // зубцы только снаружи
            else -> null
        }
    }

    private fun curtainSlit(x: Int, z: Int, y: Int): Boolean {
        if (y !in 5..7) return false
        return if (abs(x) >= halfW - 1) pmod(z, 6) == 3 else pmod(x, 6) == 3
    }

    // ---------- Круглые угловые башни с вертикальными бойницами ----------

    private fun cornerTowerBlock(x: Int, y: Int, z: Int): BlockType? {
        val dx = abs(x) - halfW
        val dz = abs(z) - halfD
        val d2 = dx * dx + dz * dz
        return when {
            y == 0 -> stone
            y in 1..cornerTowerH - 1 -> if (isSlit(dx, dz, y)) BlockType.AIR else stone
            y == cornerTowerH -> plank
            y <= cornerTowerH + 2 -> if (d2 in 16..30 && crenel(x, z)) stone else null // зубцы кольцом
            else -> null
        }
    }

    // ---------- Башни 7x7 (север/восток/запад и надвратные), вровень со стеной ----------

    private fun towerBlock(x: Int, y: Int, z: Int): BlockType? {
        val (dx, dz) = towerRel(x, z)
        return when {
            y == 0 -> stone
            y in 1..wallH - 1 -> if (isSlit(dx, dz, y)) BlockType.AIR else stone
            y == wallH -> plank                    // мосты и стенной коридор проходят поверх башни
            y <= wallH + 2 -> if ((abs(dx) == 3 || abs(dz) == 3) && crenel(x, z)) stone else null
            else -> null
        }
    }

    private fun towerRel(x: Int, z: Int): Pair<Int, Int> = when {
        abs(x) >= halfW - 3 -> (abs(x) - halfW) to z   // восточная/западная
        z <= -(halfD - 3) -> x to (z + halfD)          // северная
        else -> (abs(x) - 6) to (z - halfD)            // надвратные (центры ±6, +halfD)
    }

    // ---------- Ворота: арка, порткуллис, настил над проездом ----------

    private fun gateBlock(x: Int, y: Int, z: Int): BlockType? {
        val ax = abs(x)
        val mouth = z > halfD                          // фасад между башнями (z = 45..47)
        val top = if (mouth) gateFacadeH else wallH
        return when {
            ax == 2 -> when {                          // косяки арки
                y < top -> stone
                y == top -> plank
                y <= top + 2 -> if (z == halfD + 3 && crenel(x, z)) stone else null
                else -> null
            }
            ax <= 1 -> when {                          // проезд
                y in 1..4 -> BlockType.AIR
                y == 5 -> wood                         // деревянная балка над аркой
                y in 6..7 -> if (z >= halfD) wood else stone // поднятый порткуллис
                y < top -> stone
                y == top -> plank                      // стенной коридор проходит над воротами
                y <= top + 2 -> if (z == halfD + 3 && crenel(x, z)) stone else null
                else -> null
            }
            else -> null
        }
    }

    // ---------- Донжон: залы, лестничный колодец, арочные окна, башенки на крыше ----------

    private fun keepBlock(x: Int, y: Int, z: Int): BlockType? {
        val ax = abs(x); val az = abs(z)
        val isTurret = ax >= keepHalf - 2 && az >= keepHalf - 2        // углы 8..10
        val isWall = isTurret || ax >= keepHalf - 1 || az >= keepHalf - 1

        // Над кровлей: угловые башенки и зубцы
        if (y > keepH) {
            if (isTurret) return when {
                y <= keepH + 3 -> if (y == keepH + 2 && ax + az == keepHalf * 2 - 1) BlockType.AIR else stone
                y == keepH + 4 -> if (ax == keepHalf && az == keepHalf) wood else null
                else -> null
            }
            if (!isWall) return null
            return if (ax == keepHalf || az == keepHalf) {
                if (y <= keepH + 2 && crenel(x, z)) stone else null
            } else {
                if (y == keepH + 1) stone else null
            }
        }

        // Стены донжона (толщина 2, окна пробивают насквозь)
        if (isWall) {
            if (y == 0) return stone
            if (y == keepH) return plank               // настил крыши
            return keepWindow(x, y, z) ?: stone
        }

        // Внутренние помещения
        val sy = keepStairY(x, z)
        return when {
            y == 0 -> stone
            y == 9 || y == 18 || y == keepH -> when {  // перекрытия
                sy == y -> stone                       // ступень в уровне перекрытия
                sy != null && sy < y -> BlockType.AIR  // проём лестничного колодца
                else -> plank
            }
            sy == y -> stone                           // ступени лестницы
            z == 0 && x in -5..8 && y in 1..8 ->       // перегородка зала
                if (abs(x) <= 1 && y <= 3) BlockType.AIR else plank
            (x == -4 || x == 0 || x == 4) && abs(z) == 4 && y in 1..8 -> wood // столбы
            y <= keepH - 1 -> BlockType.AIR
            else -> null
        }
    }

    private fun keepWindow(x: Int, y: Int, z: Int): BlockType? {
        val ax = abs(x); val az = abs(z)
        val onXWall = az >= keepHalf - 1 && ax <= keepHalf - 2
        val onZWall = ax >= keepHalf - 1 && az <= keepHalf - 2
        val t = if (onXWall) ax else if (onZWall) az else return null

        // Главный вход с южной стороны (к воротам)
        if (onXWall && z > 0 && ax <= 1) {
            return when {
                y in 1..4 -> BlockType.AIR
                y == 5 -> wood
                else -> null
            }
        }
        return when {
            // арочные окна, уровень 1
            t in 3..4 && y == 3 -> wood
            t in 3..4 && y in 4..5 -> BlockType.AIR
            t == 3 && y == 6 -> BlockType.AIR          // арка
            t in 3..4 && y == 7 -> wood
            // окна, уровень 2
            t in 5..6 && y == 12 -> wood
            t in 5..6 && y in 13..14 -> BlockType.AIR
            t == 5 && y == 15 -> BlockType.AIR
            t in 5..6 && y == 16 -> wood
            // бойница, уровень 3
            t == 0 && y == 20 -> wood
            t == 0 && y in 21..22 -> BlockType.AIR
            t == 0 && y == 23 -> wood
            else -> null
        }
    }

    /** Лестничный колодец: три марша вдоль западной стены, y = 1..27. */
    private fun keepStairY(x: Int, z: Int): Int? {
        if (z < -8 || z > 8 || (z + 8) % 2 != 0) return null
        return when (x) {
            -8 -> 1 + (z + 8) / 2      // марш 1: y 1..9
            -7 -> 10 + (8 - z) / 2     // марш 2: y 10..18
            -6 -> 19 + (z + 8) / 2     // марш 3: y 19..27 (выход на крышу)
            else -> null
        }
    }

    // ---------- Мосты-переходы от донжона к башням, на столбах ----------

    private fun bridgeBlock(x: Int, y: Int, z: Int): BlockType? {
        val alongX = bridgeAxis(x, z) == 0
        val isEdge = if (alongX) abs(z) == 1 else abs(x) == 1
        val isPillar = if (alongX) (x == 16 || x == 24 || x == 32) && abs(z) <= 1
        else (z == -16 || z == -24 || z == -32) && abs(x) <= 1
        return when {
            y == 0 -> if (isPillar) stone else plank
            y in 1..11 -> if (isPillar) stone else null
            y == wallH -> plank                        // настил моста (вровень со стеной)
            y == wallH + 1 -> if (isEdge) wood else null // перила по краям
            else -> null
        }
    }

    // ---------- Хозяйственные постройки во дворе (4 шт., зеркально) ----------

    private fun houseBlock(x: Int, y: Int, z: Int): BlockType? {
        val hx = abs(x) - 26   // -6..6
        val hz = abs(z) - 26   // -5..5
        val isWall = abs(hx) == 5 || abs(hz) == 4
        return when {
            y == 6 -> if (abs(hx) <= 6 && abs(hz) <= 5) wood else null  // крыша с свесом
            y == 7 -> if (abs(hx) <= 5 && abs(hz) <= 3) wood else null
            y == 8 -> if (abs(hx) <= 4 && abs(hz) <= 1) wood else null
            y == 0 -> plank
            isWall && y in 1..5 -> when {
                hx == 0 && hz == -4 && y <= 2 -> BlockType.AIR           // дверь к центру двора
                ((abs(hz) == 4 && abs(hx) in 2..3) || (abs(hx) == 5 && abs(hz) == 2))
                        && y in 3..4 -> BlockType.AIR                    // окна
                else -> stone
            }
            y in 1..5 -> BlockType.AIR
            else -> null
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНОЕ ====================

    private val gateFacadeH = 14

    /** Вертикальная бойница на осях башни: 1 блок шириной, два яруса. */
    private fun isSlit(dx: Int, dz: Int, y: Int): Boolean {
        if (y !in 6..8 && y !in 14..16) return false
        return (dx == 0 && abs(dz) in 3..5) || (dz == 0 && abs(dx) in 3..5)
    }

    private fun pmod(v: Int, m: Int): Int = ((v % m) + m) % m

    /** Узор зубцов — зависит только от координат, поэтому клиппинг по чанкам не ломается. */
    private fun crenel(x: Int, z: Int): Boolean = pmod(x + z, 4) < 2
}