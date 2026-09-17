package io.github.zzzyyylllty.chotentech.worldgeneration.l1

/**
 * 区域边界判定 —— 「这里是不是区域的结尾」的**唯一标准**。
 *
 * ## 为什么需要它
 *
 * 之前每个走廊生成器都用自己的内部区域网格来判断边界（例如 Aquila 用 12 区块的
 * 超级块 + 2×2~6×2 的区域、Gild 用 8 区块 + 2×4~8×4、Gothic 用 12 区块 + 3×3/4×4），
 * 而 `GL1Terrain` 用的是 20×20 元区块的 `RegionType` 布局。两套判断互不相干，于是：
 *
 * - Aquila 认为自己是**内部**区块（不砌墙），隔壁区域的生成器却砌了自己的外墙
 *   → 在 Aquila 区域里直接「裸露」出别的区域的外墙，且没有对应的门洞；
 * - 反过来也会出现区域内部忽然多出一堵只砌了半边的墙。
 *
 * 现在统一以 [RegionLayout] 的 `RegionType` 为准：邻居区域类型不同 ⇒ 这一侧就是区域边界，
 * 由该区域的生成器按**自己的风格**砌墙，再由 [RegionGateway] 决定是否开门/开洞。
 *
 * 因为只依赖 RegionLayout（两侧区块算出的结果天然一致），所以交界两边永远同意，
 * 不会出现「只砌了一半」。
 */
object RegionBoundary {

    /** 北（z-1）。 */
    const val NORTH = 1

    /** 南（z+1）。 */
    const val SOUTH = 2

    /** 西（x-1）。 */
    const val WEST = 4

    /** 东（x+1）。 */
    const val EAST = 8

    /**
     * 返回该区块四周中「邻居区域类型与本区块不同」的方向位掩码。
     * 返回 0 表示这是一个纯内部区块（四周都是同一种区域）。
     */
    fun of(chunkX: Int, chunkZ: Int, seed: Long): Int {
        val self = RegionLayout.typeAt(seed, chunkX, chunkZ)
        var mask = 0
        if (RegionLayout.typeAt(seed, chunkX, chunkZ - 1) != self) mask = mask or NORTH
        if (RegionLayout.typeAt(seed, chunkX, chunkZ + 1) != self) mask = mask or SOUTH
        if (RegionLayout.typeAt(seed, chunkX - 1, chunkZ) != self) mask = mask or WEST
        if (RegionLayout.typeAt(seed, chunkX + 1, chunkZ) != self) mask = mask or EAST
        return mask
    }

    fun has(mask: Int, bit: Int): Boolean = (mask and bit) != 0
}
