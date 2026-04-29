package io.github.zzzyyylllty.chotentech.data

import org.bukkit.Location
import org.bukkit.block.Block

data class ChunkProvince (
    val world: String,
    val x: Int,
    val y: Int,
    val data: ChunkData = ChunkData(),
    var dirty: Boolean
) {
}

data class ChunkPos(
    val world: String,
    val x: Int,
    val y: Int,
)


fun asLong(x: Int, z: Int): Long {
    return (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)
}

fun fromLong(value: Long): Pair<Int, Int> {
    val x = (value and 0xFFFFFFFFL).toInt()
    val z = ((value ushr 32) and 0xFFFFFFFFL).toInt()
    return x to z
}

data class ChunkData (
    val breakableBlock: HashSet<BlockPos> = hashSetOf(),
    val unbreakableBlock: HashSet<BlockPos> = hashSetOf()
) {
}

data class BlockPos(
    val x: Int,
    val y: Int,
    val z: Int,
)

fun Block.getPos(): BlockPos {
    return BlockPos(x,y,z)
}