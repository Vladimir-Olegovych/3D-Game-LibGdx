package core.noice

import kotlin.math.*

class PerlinNoise(private val seed: Int = 42) {

    companion object {
        private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)
        private fun lerp(a: Double, b: Double, t: Double): Double = a + t * (b - a)
    }

    private val perm = IntArray(512).apply {
        val p = IntArray(256).also { pArr ->
            for (i in 0 until 256) pArr[i] = i
            // shuffle with seed
            val rnd = java.util.Random(seed.toLong())
            for (i in 255 downTo 1) {
                val j = rnd.nextInt(i + 1)
                val t = pArr[i]
                pArr[i] = pArr[j]
                pArr[j] = t
            }
        }
        for (i in 0 until 512) this[i] = p[i and 255]
    }

    private fun hash(x: Int, y: Int): Int {
        return perm[(perm[x and 255] + y) and 255]
    }

    private fun grad(hash: Int, x: Double, y: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else 0.0
        return if (h and 1 == 0) u else -u  + if (h and 2 == 0) v else -v
    }

    // Базовый перлин-шум 2D
    fun noise(x: Double, y: Double): Double {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val sx = x - x0
        val sy = y - y0

        val n00 = grad(hash(x0, y0), sx, sy)
        val n10 = grad(hash(x0 + 1, y0), sx - 1, sy)
        val n01 = grad(hash(x0, y0 + 1), sx, sy - 1)
        val n11 = grad(hash(x0 + 1, y0 + 1), sx - 1, sy - 1)

        val ix0 = lerp(n00, n10, fade(sx))
        val ix1 = lerp(n01, n11, fade(sx))
        return lerp(ix0, ix1, fade(sy))
    }

    // Фрактальный шум (fBm)
    fun fbm(x: Double, y: Double, octaves: Int = 4, persistence: Double = 0.5, lacunarity: Double = 2.0): Double {
        var value = 0.0
        var amplitude = 1.0
        var frequency = 1.0
        var maxAmp = 0.0

        for (i in 0 until octaves) {
            value += noise(x * frequency, y * frequency) * amplitude
            maxAmp += amplitude
            amplitude *= persistence
            frequency *= lacunarity
        }
        return value / maxAmp  // нормализация в [-1, 1]
    }

    // Турбулентность (абсолютное значение шума)
    fun turbulence(x: Double, y: Double, octaves: Int = 4, persistence: Double = 0.5, lacunarity: Double = 2.0): Double {
        var value = 0.0
        var amplitude = 1.0
        var frequency = 1.0
        var maxAmp = 0.0

        for (i in 0 until octaves) {
            value += abs(noise(x * frequency, y * frequency)) * amplitude
            maxAmp += amplitude
            amplitude *= persistence
            frequency *= lacunarity
        }
        return value / maxAmp
    }

    // Доменное искажение (warping) – делает узоры похожими на мрамор или жидкие завихрения
    fun warp(x: Double, y: Double, strength: Double = 0.5, octaves: Int = 2): Double {
        // Сдвигаем координаты с помощью fbm
        val angle = fbm(x, y, octaves) * 2 * PI
        val dx = cos(angle) * strength
        val dy = sin(angle) * strength
        return fbm(x + dx, y + dy, octaves)
    }

    // Красивый комбинированный шум (смесь fbm и турбулентности)
    fun beautifulNoise(x: Double, y: Double, octaves: Int = 6): Double {
        val base = fbm(x, y, octaves, 0.5, 2.0)
        val detail = turbulence(x * 2.0, y * 2.0, 3, 0.4, 2.0)
        // Эффект «свечения» и изгибов
        return (base + detail * 0.5).coerceIn(-1.0, 1.0)
    }
}