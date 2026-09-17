package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import org.bukkit.Material

/**
 * GL1PlainUnbreakable - 不可破坏的墙壁生成器（`RegionType.UNBREAKABLE`）
 *
 * 地层与 [GL1PlainWalls] 完全一致：
 *
 * ```
 * y = -4 ~ 15  石头混合（闪长岩 / 花岗岩 / 安山岩 / 石头 / 圆石）
 * y = -15 ~ -5 深板岩混合
 * ```
 *
 * 区别只有一条：**每一格额外有 [BEDROCK_CHANCE]% 的概率变成基岩**，
 * 也就是「70% 和普通墙一样 + 30% 基岩」，挖起来会处处被基岩卡住，实际等同于挖不通。
 * 哪一格是基岩同样是按位置哈希定的（确定性），所以同一个区块每次生成都一样。
 */
class GL1PlainUnbreakable(
    chunkX: Int = 0,
    chunkZ: Int = 0,
    worldSeed: Long = 0
) : GL1PlainWalls(chunkX, chunkZ, worldSeed) {

    override fun blockAt(x: Int, y: Int, z: Int, base: Material): Material =
        if (hash(x, y, z, SALT_BEDROCK) % 100 < BEDROCK_CHANCE) Material.BEDROCK else base

    companion object {
        private const val SALT_BEDROCK = 5302L

        /** 在普通墙的成分之上，额外掺入基岩的比例（%）。 */
        const val BEDROCK_CHANCE = 30
    }
}
