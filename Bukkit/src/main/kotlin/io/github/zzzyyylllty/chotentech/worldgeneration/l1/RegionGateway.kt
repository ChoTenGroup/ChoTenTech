package io.github.zzzyyylllty.chotentech.worldgeneration.l1

import io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors.CorridorMaze
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.generator.ChunkGenerator.ChunkData

/**
 * 区域交界通道的**规划器**（只有计划与装门，不再自己去挖方块）。
 *
 * ## 为什么要改成「生成器自己留门洞」
 *
 * 最初的做法是：交界墙由区域生成器砌好，再由本类**从边缘往里挖**出门洞。这带来三个问题：
 *
 * 1. 交界两侧都装门 → 出现**两层门**；
 * 2. 挖的时候不知道墙有多厚，只能「一直挖到空气」→ 可能穿过生成器内部的隔墙，
 *    把**旁边房间的墙/地形挖掉**；
 * 3. 双开门的合页方向只按「第几个门」判断，没考虑门朝向 → 有些方向的两扇门合页挤在中间。
 *
 * 现在改成：本类只负责**算**（形态、位置、合页、谁装门），生成器在砌自己那道交界墙时
 * 调用 [opensAt] 把这些格子**跳过**（门洞就自然留出来了，不会多挖一格），
 * 再由**坐标较大的一侧**调用 [placeDoors] 装上唯一的一层门。
 *
 * 因为交界两侧都由 `(坐标较大的一侧)` 这个共享 key 推算，形态与位置必然一致，
 * 不会出现一侧开洞另一侧不开的情况。
 */
object RegionGateway {

    /** 通道形态。 */
    enum class Passage { NONE, SINGLE_DOOR, DOUBLE_DOOR, HOLE }

    /** 门洞从这一层开始（与地板 y=0 齐平）。 */
    const val PASSAGE_BOTTOM = 1

    private const val SALT_GATE_X = 3001L
    private const val SALT_GATE_Z = 3002L

    /** 子区域（生成器内部切出来的小区域）之间的通道用不同的 salt，避免和区域交界撞在一起。 */
    private const val SALT_SUB_X = 3003L
    private const val SALT_SUB_Z = 3004L

    /** 交界门的默认（木门）材质。 */
    private val DOOR_MATERIAL = Material.OAK_DOOR

    /** 铁门材质：有 [IRON_DOOR_CHANCE]% 的交界门会变成铁门。 */
    private val IRON_DOOR_MATERIAL = Material.IRON_DOOR

    /** 铁门占比（%）。双开门的两扇由同一个方案决定，所以一定会一起变成铁门。 */
    private const val IRON_DOOR_CHANCE = 5

    /** 实心填充区域：与它们相邻时不开门（门后是实心方块）。 */
    private val SOLID_REGIONS = setOf(RegionType.WALLS, RegionType.UNBREAKABLE)

    /**
     * 开孔允许落在的横向区间（含两端）。
     *
     * 取的是**最窄的那个内部通道**：`GL1PlainCorridor` 的一般走廊是 4 格宽（`corridorThickness = 6`
     * → 内部 6..9）。开孔跑到这个区间外，就会开在走廊两侧的墙里，门后是墙、过不去。
     */
    private const val DOOR_ZONE_MIN = 6
    private const val DOOR_ZONE_MAX = 9

    /**
     * 合页方向修正表。
     *
     * 双开门希望两扇的合页都在**外侧**。合页的 LEFT/RIGHT 是相对门朝向定义的，
     * 如果某一侧装出来合页挤在中间，把那个方向加进来即可（只影响双开门）。
     */
    private val HINGE_FLIP = setOf<Int>()

    /** 一条交界上的通道方案。 */
    class Plan internal constructor(
        val side: Int,
        val passage: Passage,
        /** 沿墙方向的开孔区间 [gapStart, gapEnd)。NONE 时为空区间。 */
        val gapStart: Int,
        val gapEnd: Int,
        /** 开孔高度（门 2、洞口 3）。 */
        val height: Int,
        /** 是否由本侧负责装门（只有一侧装，避免两层门）。 */
        val doorOwner: Boolean,
        val facing: BlockFace,
        /** 门材质。双开门两扇共用这一个值，所以要么都是木门、要么都是铁门。 */
        val doorMaterial: Material,
        /** 沿轴坐标 → 合页。单门 1 个、双门 2 个、洞口/无门 0 个。 */
        val doors: List<Pair<Int, Door.Hinge>>
    ) {
        /** 生成器砌墙时用它判断这个墙位要不要留空。 */
        fun opensAt(i: Int, y: Int): Boolean =
            i in gapStart until gapEnd && y in PASSAGE_BOTTOM until (PASSAGE_BOTTOM + height)
    }

    /**
     * 规划某个方向的交界通道。
     *
     * @return null 表示这一侧不是区域边界（或者与实心区域相邻），生成器按普通墙处理即可
     */
    fun planFor(chunkX: Int, chunkZ: Int, seed: Long, side: Int): Plan? {
        if (!RegionBoundary.has(RegionBoundary.of(chunkX, chunkZ, seed), side)) return null

        val dx = when (side) {
            RegionBoundary.EAST -> 1
            RegionBoundary.WEST -> -1
            else -> 0
        }
        val dz = when (side) {
            RegionBoundary.SOUTH -> 1
            RegionBoundary.NORTH -> -1
            else -> 0
        }

        val self = RegionLayout.typeAt(seed, chunkX, chunkZ)
        val neighbour = RegionLayout.typeAt(seed, chunkX + dx, chunkZ + dz)

        val facing = when (side) {
            RegionBoundary.NORTH -> BlockFace.NORTH
            RegionBoundary.SOUTH -> BlockFace.SOUTH
            RegionBoundary.WEST -> BlockFace.WEST
            else -> BlockFace.EAST
        }

        // 只有坐标较大的一侧装门：两侧都装就成了两层门
        val owner = dz < 0 || dx < 0

        // 与实心区域相邻：这一侧仍然是区域边界（生成器照常砌墙），但不开门
        if (self in SOLID_REGIONS || neighbour in SOLID_REGIONS) {
            return Plan(side, Passage.NONE, 0, 0, 0, owner, facing, DOOR_MATERIAL, emptyList())
        }

        // 走廊（CORRIDORS 区域）的迷宫开口必须能真的过去。
        // 区域布局很碎（约 93% 的区块至少有一侧贴着区域边界），而区域边界这一侧的迷宫开口
        // 是被压掉的、只由本条交界决定；如果这里再抽到「无门」，走廊格子就可能四面全封，
        // 变成进不去的房间。所以：交界任一侧是走廊、且它的迷宫在这条边上开口 → 强制开通道。
        //
        // CorridorMaze 与 RegionBoundary 的方向位取值一致（UP=NORTH=1、DOWN=SOUTH=2、
        // LEFT=WEST=4、RIGHT=EAST=8），所以 side 可以直接用。
        val mazeWantsOpen = mazeOpensAt(chunkX, chunkZ, seed, side) ||
                mazeOpensAt(chunkX + dx, chunkZ + dz, seed, oppositeSide(side))

        // 边界 key 取坐标较大的一侧：交界两侧算出来完全一致
        val keyX = if (dx > 0) chunkX + 1 else chunkX
        val keyZ = if (dz > 0) chunkZ + 1 else chunkZ
        val alongZ = dz != 0
        val h = hash(seed, keyX, keyZ, if (alongZ) SALT_GATE_Z else SALT_GATE_X)

        return buildPlan(side, owner, h, mazeWantsOpen)
    }

    /**
     * 规划一条**子区域**边界上的通道。
     *
     * 容器被切成几个子区域后，子区域之间也要能走通，否则大地形就成了一堆密封盒子。
     * 形态 / 尺寸 / 合页规则与区域交界完全一致，只是 key 的 salt 不同。
     *
     * @param sub 该区块所在的子区域（null 表示没有子区域，直接返回 null）
     */
    fun planForSubRegion(
        chunkX: Int,
        chunkZ: Int,
        seed: Long,
        side: Int,
        sub: SubRegionLayout.Sub?
    ): Plan? {
        if (sub == null) return null

        // 关键：只有「这一侧确实是该子区域的边」才可能有通道。
        // 漏掉这个判断时，子区域内部的区块四边都会拿到方案 → 会在没有墙的地方凭空装门。
        val onEdge = when (side) {
            RegionBoundary.NORTH -> chunkZ == sub.z
            RegionBoundary.SOUTH -> chunkZ == sub.z + sub.h - 1
            RegionBoundary.WEST -> chunkX == sub.x
            else -> chunkX == sub.x + sub.w - 1
        }
        if (!onEdge) return null

        val dx = when (side) {
            RegionBoundary.EAST -> 1
            RegionBoundary.WEST -> -1
            else -> 0
        }
        val dz = when (side) {
            RegionBoundary.SOUTH -> 1
            RegionBoundary.NORTH -> -1
            else -> 0
        }

        val keyX = if (dx > 0) chunkX + 1 else chunkX
        val keyZ = if (dz > 0) chunkZ + 1 else chunkZ
        val alongZ = dz != 0
        val h = hash(seed, keyX, keyZ, if (alongZ) SALT_SUB_Z else SALT_SUB_X)

        return buildPlan(side, dz < 0 || dx < 0, h, forcePassage = false)
    }

    /** 该区块如果是走廊区域，且它的迷宫在这一侧是开的 → true。 */
    private fun mazeOpensAt(chunkX: Int, chunkZ: Int, seed: Long, side: Int): Boolean {
        if (RegionLayout.typeAt(seed, chunkX, chunkZ) != RegionType.CORRIDORS) return false
        return CorridorMaze.isOpen(CorridorMaze.openings(chunkX, chunkZ, seed), side)
    }

    private fun oppositeSide(side: Int): Int = when (side) {
        RegionBoundary.NORTH -> RegionBoundary.SOUTH
        RegionBoundary.SOUTH -> RegionBoundary.NORTH
        RegionBoundary.WEST -> RegionBoundary.EAST
        else -> RegionBoundary.WEST
    }

    private fun buildPlan(side: Int, owner: Boolean, h: Int, forcePassage: Boolean): Plan {
        val facing = when (side) {
            RegionBoundary.NORTH -> BlockFace.NORTH
            RegionBoundary.SOUTH -> BlockFace.SOUTH
            RegionBoundary.WEST -> BlockFace.WEST
            else -> BlockFace.EAST
        }

        val passage = if (forcePassage) {
            // 必须能过去：在三种可通行形态里选（不含「无门」）
            when (h % 3) {
                0 -> Passage.SINGLE_DOOR
                1 -> Passage.DOUBLE_DOOR
                else -> Passage.HOLE
            }
        } else {
            when (h % 100) {
                in 0..39 -> Passage.NONE
                in 40..64 -> Passage.SINGLE_DOOR
                in 65..84 -> Passage.DOUBLE_DOOR
                else -> Passage.HOLE
            }
        }
        val center = when (passage) {
            // 开孔必须落在**走廊那 4 格宽的过道**里（DOOR_ZONE_MIN..DOOR_ZONE_MAX）。
            // 取 5..10 时会有 1/3 的门开在走廊两侧的墙里，门后面还是墙、走不过去；
            // 其他生成器的内部通道都比这更宽，所以收窄到 6..9 对它们没有影响。
            // 洞口是固定 4 格宽（见下面 HOLE 分支），用不到 center。
            Passage.DOUBLE_DOOR -> DOOR_ZONE_MIN + ((h / 100) % 3)         // 6..8 → 双门 6..9
            else -> DOOR_ZONE_MIN + ((h / 100) % 4)                        // 6..9 → 单门 6..9
        }

        // 合页：沿轴靠「低坐标那端」的那扇门，合页要落在低端（外侧），这样两扇门的合页在外、
        // 门缝在中间。
        //
        // 方向对应（按实机反馈校准）：门的 hinge 是**相对门自身朝向**定义的 ——
        // 「门朝外看时自己的左手边」。于是对低端那扇门：
        //   北墙(facing=NORTH)：门的左手边 = 西 = 低端 → LEFT
        //   南墙(facing=SOUTH)：门的左手边 = 东 = 高端 → RIGHT
        //   西墙(facing=WEST) ：门的左手边 = 南 = 高端 → RIGHT
        //   东墙(facing=EAST) ：门的左手边 = 北 = 低端 → LEFT
        // 如果实机发现某一侧仍然是合页朝内，把该方向加进 [HINGE_FLIP] 即可。
        val hingeLowRaw = if (side == RegionBoundary.NORTH || side == RegionBoundary.EAST) {
            Door.Hinge.LEFT
        } else {
            Door.Hinge.RIGHT
        }
        val hingeLow = if (side in HINGE_FLIP) opposite(hingeLowRaw) else hingeLowRaw
        val hingeHigh = opposite(hingeLow)

        // 门材质也由共享 key 决定：交界两侧一致；双开门两扇共用同一个值，
        // 所以「双开门两扇一起变铁门」是天然成立的。
        val doorMaterial = if ((h / 1000) % 100 < IRON_DOOR_CHANCE) IRON_DOOR_MATERIAL else DOOR_MATERIAL

        return when (passage) {
            Passage.NONE -> Plan(side, passage, 0, 0, 0, owner, facing, doorMaterial, emptyList())

            // 洞口 = 4 格宽，和走廊内部（同样是 4 格）对齐；门洞仍然是 1~2 格（那是门）
            Passage.HOLE -> Plan(
                side, passage, DOOR_ZONE_MIN, DOOR_ZONE_MAX + 1, 3, owner, facing, doorMaterial, emptyList()
            )

            Passage.SINGLE_DOOR -> Plan(
                side, passage, center, center + 1, 2, owner, facing, doorMaterial,
                listOf(center to if ((h / 7) % 2 == 0) hingeLow else hingeHigh)
            )

            Passage.DOUBLE_DOOR -> Plan(
                side, passage, center, center + 2, 2, owner, facing, doorMaterial,
                listOf(center to hingeLow, center + 1 to hingeHigh)
            )
        }
    }

    /** 生成器砌墙时调用：这个墙位要留空吗（plan 为 null 表示不是交界，返回 false）。 */
    fun opensAt(plan: Plan?, i: Int, y: Int): Boolean = plan?.opensAt(i, y) ?: false

    /**
     * 装上这一侧的门（只有 `doorOwner` 为 true 时才真的放，避免两层门）。
     * 应在砌完墙之后调用。
     */
    fun placeDoors(chunkData: ChunkData, plan: Plan?) {
        if (plan == null || !plan.doorOwner) return
        for ((i, hinge) in plan.doors) {
            if (i !in 0..15) continue
            val x: Int
            val z: Int
            when (plan.side) {
                RegionBoundary.NORTH -> { x = i; z = 0 }
                RegionBoundary.SOUTH -> { x = i; z = 15 }
                RegionBoundary.WEST -> { x = 0; z = i }
                else -> { x = 15; z = i }
            }
            placeDoor(chunkData, x, z, plan.facing, hinge, plan.doorMaterial)
        }
    }

    private fun placeDoor(
        chunkData: ChunkData,
        x: Int,
        z: Int,
        facing: BlockFace,
        hinge: Door.Hinge,
        material: Material
    ) {
        val lower = material.createBlockData() as Door
        lower.facing = facing
        lower.hinge = hinge
        lower.isOpen = false
        lower.half = Bisected.Half.BOTTOM
        chunkData.setBlock(x, PASSAGE_BOTTOM, z, lower)

        val upper = material.createBlockData() as Door
        upper.facing = facing
        upper.hinge = hinge
        upper.isOpen = false
        upper.half = Bisected.Half.TOP
        chunkData.setBlock(x, PASSAGE_BOTTOM + 1, z, upper)
    }

    private fun opposite(hinge: Door.Hinge): Door.Hinge =
        if (hinge == Door.Hinge.LEFT) Door.Hinge.RIGHT else Door.Hinge.LEFT

    private fun hash(seed: Long, a: Int, b: Int, salt: Long): Int {
        var h = seed xor salt
        h = h * 6364136223846793005L + a
        h = h * 6364136223846793005L + b
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return (h and 0x7FFFFFFF).toInt()
    }
}
