package io.github.zzzyyylllty.chotentech.worldgeneration.l1

import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 生成器内部区域的「容器 + 子区域」布局。
 *
 * ## 规则
 *
 * 生成器原本的 `regionSizes`（例如 Ouroboros 的 2×2 / 2×3 / 2×4）现在含义变了：
 *
 * 1. 滚到的尺寸 **×2** 当作一个**容器**（2×2 → 4×4）。容器就是「一个地形」，
 *    外圈由生成器按自己的风格砌墙 —— 地形整体因此变大一倍；
 * 2. 容器内部再用**原始尺寸**的子区域**尽量铺满**，子区域的边界同样砌墙（内墙），
 *    于是一个大地形里会分成几个小区域 / 小房间。
 *
 * 布局完全由 `(worldSeed, 容器原点, 容器尺寸)` 决定并带缓存，因此：
 * - 同一个容器每次算出来一样（区块生成是并发的，必须确定性）；
 * - 子区域不会跨容器，也不会互相重叠。
 *
 * 用法：生成器在判断「这一侧要不要砌墙」时，除了自己的容器边界与 [RegionBoundary]，
 * 再把子区域的边界并进去即可。
 */
object SubRegionLayout {

    /** 子区域（绝对区块坐标）。 */
    class Sub(val x: Int, val z: Int, val w: Int, val h: Int)

    private class Fill(val owner: IntArray, val subs: List<Sub>, val containerH: Int)

    private data class Key(val seed: Long, val x: Int, val z: Int, val w: Int, val h: Int)

    private const val SALT_SUBREGION = 4101L
    private const val MAX_CACHED = 1024

    private val cache = ConcurrentHashMap<Key, Fill>()

    /**
     * 取该区块所在容器内、包含它的子区域。
     *
     * @return null 表示这一格没有被任何子区域覆盖（铺满算法偶尔会剩一两格，属正常）
     */
    fun of(
        chunkX: Int,
        chunkZ: Int,
        seed: Long,
        containerX: Int,
        containerZ: Int,
        containerW: Int,
        containerH: Int,
        sizes: List<Pair<Int, Int>>
    ): Sub? {
        if (containerW <= 0 || containerH <= 0 || sizes.isEmpty()) return null
        val lx = chunkX - containerX
        val lz = chunkZ - containerZ
        if (lx !in 0 until containerW || lz !in 0 until containerH) return null

        val fill = fillOf(seed, containerX, containerZ, containerW, containerH, sizes)
        val id = fill.owner[lx * fill.containerH + lz]
        if (id < 0) return null
        val sub = fill.subs[id]
        return Sub(sub.x, sub.z, sub.w, sub.h)
    }

    private fun fillOf(
        seed: Long,
        x: Int, z: Int, w: Int, h: Int,
        sizes: List<Pair<Int, Int>>
    ): Fill {
        if (cache.size > MAX_CACHED) cache.clear()
        return cache.getOrPut(Key(seed, x, z, w, h)) { buildFill(seed, x, z, w, h, sizes) }
    }

    /** 扫描式贪心铺满：每个空格优先选「正好填满剩余宽度/高度」的尺寸，否则在最大的两个里随机。 */
    private fun buildFill(
        seed: Long,
        containerX: Int, containerZ: Int,
        containerW: Int, containerH: Int,
        sizes: List<Pair<Int, Int>>
    ): Fill {
        val owner = IntArray(containerW * containerH) { -1 }
        val subs = mutableListOf<Sub>()
        val rand = Random(mix(seed, containerX, containerZ))

        val usable = sizes
            .filter { it.first in 1..containerW && it.second in 1..containerH }
            .sortedByDescending { it.first * it.second }
        if (usable.isEmpty()) return Fill(owner, subs, containerH)

        fun free(x: Int, z: Int, w: Int, h: Int): Boolean {
            if (x + w > containerW || z + h > containerH) return false
            for (i in x until x + w) {
                for (j in z until z + h) {
                    if (owner[i * containerH + j] != -1) return false
                }
            }
            return true
        }

        for (x in 0 until containerW) {
            for (z in 0 until containerH) {
                if (owner[x * containerH + z] != -1) continue
                val candidates = usable.filter { free(x, z, it.first, it.second) }
                if (candidates.isEmpty()) continue

                // 1) 优先选「两个方向都能整除剩余空间」的尺寸（最能铺满，例如 6x6 用 3x3）；
                // 2) 其次选能正好收边的；3) 最后在最大的两个里随机（保留变化）
                val pick = candidates.firstOrNull {
                    (containerH - z) % it.second == 0 && (containerW - x) % it.first == 0
                } ?: candidates.firstOrNull {
                    it.second == containerH - z || it.first == containerW - x
                } ?: candidates[rand.nextInt(minOf(2, candidates.size))]

                val (w, h) = pick
                val id = subs.size
                subs.add(Sub(x, z, w, h))
                for (i in x until x + w) {
                    for (j in z until z + h) {
                        owner[i * containerH + j] = id
                    }
                }
            }
        }
        return Fill(owner, subs, containerH)
    }

    private fun mix(seed: Long, a: Int, b: Int): Long {
        var h = seed xor SALT_SUBREGION
        h = h * 6364136223846793005L + a
        h = h * 6364136223846793005L + b
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return h
    }
}
