package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_FLOOR_MIX_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.fillLayer
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldRoof
import io.github.zzzyyylllty.chotentech.worldgeneration.setDoublePlant
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionBoundary
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionGateway
import org.bukkit.Axis
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.Orientable
import org.bukkit.block.data.type.Bell
import org.bukkit.block.data.type.Candle
import org.bukkit.block.data.type.Lantern
import org.bukkit.block.data.type.Slab
import org.bukkit.generator.ChunkGenerator.ChunkData

/**
 * GL1PlainCorridor - 迷宫式走廊生成器（带破败装饰）
 *
 * 连通方向参考 `tinf/1corridors.kt`：由 [CorridorMaze] 的生成树决定每个区块上下左右哪些边是开的。
 *
 * 三种形态（按开口数）：
 * - `CORRIDOR`（开口 >= 2）：**4 格宽**的窄走廊（[corridorThickness] = 6 → 内部 4×4）
 * - `ROOM`（开口 >= 2 且抽中）：房间，内部 12×12，通道更宽
 * - `CLOSED_ROOM`（开口 == 1）：尽端小间，塞点物资
 *
 * **走廊预设**（[presetAt]，按区块中心的平滑噪声选，同一片区域大多同一种）：
 * - `NORMAL`（约 50%，最多）：干净的混凝土/磨制闪长岩墙，不掺杂色，只有普通家具
 * - `OVERGROWN`（约 15%）：苔藓 / 苔藓毯 / 藤蔓 / 地毯，墙体轻微斑驳并混少量铁栏杆（裸露钢筋）
 * - `TERRACOTTA`（约 15%）：墙体与地面改用陶瓦
 * - `STONE`（约 10%）：圆石 / 安山岩 / 砂砾
 * - `STONE_BRICKS`（约 5%）：石砖 / 裂石砖 / 苔石砖
 * - `DIRT`（约 5%）：泥土 / 草方块盖住墙体和地面，长草、蕨、枯木、蘑菇
 *
 * 所有装饰与预设选择都是**确定性**的（同一区块每次生成一致，见 [hash] / [rawHash]）。
 *
 * **贴墙装饰**（[generateWallDecor]）：沿墙按世界坐标每 4 格一个装饰位，摆壁挂火把 / 灵魂火把 /
 * 红石火把、顺着墙拉的铁链「管线」（下面常吊一盏灯）、吊灯、天花板铃铛，内部空间里还有
 * 贴墙的花盆 / 蜡烛 / 陶罐 / 木桶箱子一类的杂物 —— 一律贴在墙上或墙根，不挡路。
 * 朝向按原版规矩：`facing` = 从墙指向房间里。
 *
 * **有门 / 洞口的一侧是过道**（[buildBand]）：区域交界那一侧如果留了通道（单门/双门/洞口），
 * 整条墙带会打成过道，只在贴着交界的那 1 格砌墙（且只有 `doorOwner` 一侧砌），门就装在这道墙上。
 * 以前门洞只开在 6 格厚墙的正中，能走的只有区块正中的 4×4，门还常常开在两侧的墙里、后面还是墙。
 */
class GL1PlainCorridor(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    // 混合材质权重配置
    private val stoneWeights = listOf(3, 2, 1)
    private val deepslateWeights = listOf(5, 3, 2)
    /** 一般走廊的墙厚：16 - 2×6 = 4 格宽（原来是 2，内部 12 格太宽）。 */
    private val corridorThickness = 6

    /** 房间的墙厚：留出 12×12 的空间。 */
    private val roomThickness = 2

    /** 墙体高度（地板在 y=0，天花板在 y=6）。 */
    private val wallBottomY = 1
    private val wallTopY = 5

    /** 通道宽度：走廊和房间统一 4 格（= 走廊内部宽度），不再出现 3 格或 5 格的窄口/宽口。 */
    private val corridorGap = 4
    private val roomGap = 4

    /** 地毯颜色（随机取）。 */
    private val carpets = listOf(
        Material.LIGHT_GRAY_CARPET,
        Material.GRAY_CARPET,
        Material.WHITE_CARPET,
        Material.BROWN_CARPET,
        Material.YELLOW_CARPET
    )

    override fun generate(chunkData: ChunkData) {
        val openings = CorridorMaze.openings(chunkX, chunkZ, worldSeed)
        val type = CorridorMaze.type(chunkX, chunkZ, worldSeed)

        // 区块级「走廊预设」：用平滑噪声选，所以同一片区域大多数区块是同一种风格
        val preset = presetAt(chunkX * 16 + 8, chunkZ * 16 + 8)

        // 区域边界那一侧一律砌满墙（CorridorMaze 与 RegionBoundary 的方向位取值一致）
        val regionBoundary = RegionBoundary.of(chunkX, chunkZ, worldSeed)
        val effective = openings and regionBoundary.inv()

        // 四面交界方案先算好：砌墙要用，家具避让门洞也要用
        val plans = arrayOf(
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.NORTH),
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.SOUTH),
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.WEST),
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.EAST)
        )
        markDoorFront(plans, openArea(type))

        val open = openArea(type)

        generateFoundation(chunkData)
        generateFloor(chunkData, preset, open)
        generateCeiling(chunkData)
        generateMazeWalls(chunkData, effective, type, preset, plans)
        generateCeilingOpenings(chunkData, effective)

        if (preset == CorridorPreset.OVERGROWN) generateCarpets(chunkData, open)
        generateFurniture(chunkData, open, preset)

        // 贴墙装饰放在家具之后：它只填还是空气的格子，不会盖掉已经摆好的东西
        generateWallDecor(chunkData, open, preset)

        when (type) {
            CorridorChunkType.ROOM -> generateRoomInterior(chunkData, open)
            CorridorChunkType.CLOSED_ROOM -> generateDeadEndInterior(chunkData, open)
            else -> Unit
        }

        // 藤蔓放最后：它只填空气，不能盖住上面的地毯/家具
        if (preset == CorridorPreset.OVERGROWN || preset == CorridorPreset.DIRT) {
            generateVines(chunkData, open)
        }
    }

    /**
     * 门洞正前方那几格：家具一律不放。
     *
     * 走廊内部只有 4 格宽，一张木板堆（2~3 格长）贴着墙摆下去正好能把门洞封死，
     * 于是门后面成了一个进不去的小口袋。所以把「门洞正对着的那一条内部格」整条留空。
     */
    private val doorFront = Array(16) { BooleanArray(16) }

    private fun markDoorFront(plans: Array<RegionGateway.Plan?>, open: IntRange) {
        for (side in 0..3) {
            val plan = plans[side] ?: continue
            for (i in plan.gapStart until plan.gapEnd) {
                if (i !in 0..15) continue
                when (side) {
                    0 -> doorFront[i][open.first] = true      // 北门洞 → 内部靠北那一条
                    1 -> doorFront[i][open.last] = true       // 南
                    2 -> doorFront[open.first][i] = true      // 西
                    else -> doorFront[open.last][i] = true    // 东
                }
            }
        }
    }

    // ==================== 走廊预设 ====================

    /**
     * 走廊预设（风格）。
     *
     * 用平滑噪声（[valueNoise]）选，所以同一片区域大多是同一种；[NORMAL] 占比最大。
     */
    private enum class CorridorPreset {
        /** 完全正常：干净的混凝土 / 磨制闪长岩墙，只有普通家具。 */
        NORMAL,

        /** 藤蔓斑驳：苔藓、藤蔓、地毯，墙体轻微斑驳并混少量钢筋。 */
        OVERGROWN,

        /** 陶瓦。 */
        TERRACOTTA,

        /** 石头。 */
        STONE,

        /** 石砖。 */
        STONE_BRICKS,

        /** 泥土 / 草方块覆盖，长草和枯木。 */
        DIRT,
    }

    private fun presetAt(wx: Int, wz: Int): CorridorPreset {
        val n = valueNoise(wx, wz, PRESET_CELL, SALT_PRESET)
        return when {
            n < 0.50 -> CorridorPreset.NORMAL          // 最多
            n < 0.60 -> CorridorPreset.OVERGROWN
            n < 0.70 -> CorridorPreset.TERRACOTTA
            n < 0.79 -> CorridorPreset.STONE
            n < 0.845 -> CorridorPreset.STONE_BRICKS
            else -> CorridorPreset.DIRT
        }
    }

    /** 平滑值噪声（双线性 + smoothstep），返回 0..1。 */
    private fun valueNoise(wx: Int, wz: Int, cellSize: Int, salt: Long): Double {
        val x0 = Math.floorDiv(wx, cellSize)
        val z0 = Math.floorDiv(wz, cellSize)
        val x1 = x0 + 1
        val z1 = z0 + 1
        val fx = (wx - x0 * cellSize).toDouble() / cellSize
        val fz = (wz - z0 * cellSize).toDouble() / cellSize

        val inv = 1.0 / Int.MAX_VALUE.toDouble()
        val v00 = rawHash(x0, 0, z0, salt).toDouble() * inv
        val v10 = rawHash(x1, 0, z0, salt).toDouble() * inv
        val v01 = rawHash(x0, 0, z1, salt).toDouble() * inv
        val v11 = rawHash(x1, 0, z1, salt).toDouble() * inv

        val sx = fx * fx * (3.0 - 2.0 * fx)
        val sz = fz * fz * (3.0 - 2.0 * fz)

        val a = v00 * (1.0 - sx) + v10 * sx
        val b = v01 * (1.0 - sx) + v11 * sx
        return a * (1.0 - sz) + b * sz
    }

    // ==================== 内部活动区 ====================

    /** 该类型的内部活动区（墙以内），返回 [min, max]（含）。 */
    private fun openArea(type: CorridorChunkType): IntRange {
        val t = if (type == CorridorChunkType.ROOM) roomThickness else corridorThickness
        return t..(15 - t)
    }

    // ==================== 迷宫墙体 ====================

    private fun generateMazeWalls(
        chunkData: ChunkData,
        openings: Int,
        type: CorridorChunkType,
        preset: CorridorPreset,
        plans: Array<RegionGateway.Plan?>
    ) {
        val room = type == CorridorChunkType.ROOM
        val gap = if (room) roomGap else corridorGap
        val thickness = if (room) roomThickness else corridorThickness
        val gapStart = (16 - gap) / 2

        val planN = plans[0]
        val planS = plans[1]
        val planW = plans[2]
        val planE = plans[3]

        buildBand(chunkData, alongZ = true, lowSide = true, gapStart, gap, thickness, preset,
            open = CorridorMaze.isOpen(openings, CorridorMaze.UP), plan = planN)
        buildBand(chunkData, alongZ = true, lowSide = false, gapStart, gap, thickness, preset,
            open = CorridorMaze.isOpen(openings, CorridorMaze.DOWN), plan = planS)
        buildBand(chunkData, alongZ = false, lowSide = true, gapStart, gap, thickness, preset,
            open = CorridorMaze.isOpen(openings, CorridorMaze.LEFT), plan = planW)
        buildBand(chunkData, alongZ = false, lowSide = false, gapStart, gap, thickness, preset,
            open = CorridorMaze.isOpen(openings, CorridorMaze.RIGHT), plan = planE)

        RegionGateway.placeDoors(chunkData, planN)
        RegionGateway.placeDoors(chunkData, planS)
        RegionGateway.placeDoors(chunkData, planW)
        RegionGateway.placeDoors(chunkData, planE)
    }

    private fun buildBand(
        chunkData: ChunkData,
        alongZ: Boolean,
        lowSide: Boolean,
        gapStart: Int,
        gap: Int,
        thickness: Int,
        preset: CorridorPreset,
        open: Boolean,
        plan: RegionGateway.Plan?
    ) {
        // 「有门的一侧」整条墙带变成**过道**：除了交界那一格厚的墙，其余全部留空。
        // 以前门洞只开在 6 格厚墙的正中间，于是门前后都是墙、能走的只有区块正中的 4×4；
        // 现在把这一侧的墙带打通成过道，门就装在最外侧那道 1 格厚的墙上，门后是真通道。
        //
        // 只有「装门的那一侧」（plan.doorOwner）砌这道 1 格厚的墙，
        // 交界另一侧全空即可，否则交界线上会叠出两层墙 / 两层门。
        val passageSide = plan?.passage?.let { it != RegionGateway.Passage.NONE } ?: false
        val edgeWall = passageSide && plan?.doorOwner == true

        for (i in 0..15) {
            val mazeGap = open && i in gapStart until (gapStart + gap)

            for (y in wallBottomY..wallTopY) {
                if (mazeGap) continue
                if (RegionGateway.opensAt(plan, i, y)) continue

                for (t in 0 until thickness) {
                    // 过道侧：t=0 就是贴近交界的那一层（两侧对称，见下面的 x/z 计算）
                    if (passageSide && (!edgeWall || t != 0)) continue
                    val x: Int
                    val z: Int
                    if (alongZ) {
                        x = i
                        z = if (lowSide) t else 15 - t
                    } else {
                        x = if (lowSide) t else 15 - t
                        z = i
                    }
                    chunkData.setBlock(x, y, z, wallMaterialFor(preset, x, y, z))
                }
            }
        }
    }

    /**
     * 墙体材质，按预设分风格。
     *
     * **底色永远是普通走廊那道墙**（y1 磨制闪长岩、y2~5 白色混凝土）。
     * 主题预设（陶瓦 / 石头 / 石砖 / 泥土）只做**小修小改**：
     * 按 3×3 的方块算「这一小片是不是补丁」，只有 [PATCH_CHANCE]% 的小片会换成风格材质，
     * 绝不再整面墙、整个地板地换（之前那样太离谱了）。
     *
     * [CorridorPreset.OVERGROWN] 例外：它就是「藤蔓斑驳」那一款，保持原来的轻微杂色。
     */
    private fun wallMaterialFor(preset: CorridorPreset, x: Int, y: Int, z: Int): Material {
        val base = if (y == wallBottomY) Material.POLISHED_DIORITE else Material.WHITE_CONCRETE
        if (preset == CorridorPreset.NORMAL) return base

        if (preset == CorridorPreset.OVERGROWN) {
            val h = hash(x, y, z, SALT_WALL) % 100
            return when {
                h < 3 -> Material.IRON_BARS            // 裸露的钢筋
                h < 6 -> Material.COBBLESTONE
                h < 9 -> Material.MOSSY_COBBLESTONE
                h < 11 -> Material.CRACKED_STONE_BRICKS
                h < 13 -> Material.GRAY_CONCRETE
                else -> base
            }
        }

        // 主题预设：只有落在「补丁片」里的那一小片才换材质
        if (!inPatch(x, z, SALT_WALL)) return base
        // 补丁内部按整列选材质，做成一小片同色，而不是撒胡椒面
        val col = hash(x, 0, z, SALT_WALL) % 100
        return when (preset) {
            CorridorPreset.TERRACOTTA -> when (col) {
                in 0..54 -> Material.TERRACOTTA
                in 55..74 -> Material.LIGHT_GRAY_TERRACOTTA
                in 75..89 -> Material.WHITE_TERRACOTTA
                else -> Material.BROWN_TERRACOTTA
            }

            CorridorPreset.STONE -> when (col) {
                in 0..59 -> Material.STONE
                in 60..79 -> Material.COBBLESTONE
                in 80..89 -> Material.ANDESITE
                else -> Material.GRAVEL
            }

            CorridorPreset.STONE_BRICKS -> when (col) {
                in 0..69 -> Material.STONE_BRICKS
                in 70..84 -> Material.CRACKED_STONE_BRICKS
                in 85..94 -> Material.MOSSY_STONE_BRICKS
                else -> Material.CHISELED_STONE_BRICKS
            }

            // 泥土：一小片被土/草盖住（不是整面墙都糊上土）
            CorridorPreset.DIRT -> when (col) {
                in 0..44 -> Material.DIRT
                in 45..69 -> Material.COARSE_DIRT
                in 70..89 -> Material.GRASS_BLOCK
                else -> Material.PODZOL
            }

            CorridorPreset.NORMAL, CorridorPreset.OVERGROWN -> base
        }
    }

    /**
     * 风格补丁：**用平滑噪声画**，所以是一小片一小片、边缘圆滑地铺开，
     * 而不是一格一格的 3×3 方块（那看着太假）。
     *
     * 两层噪声叠加：主层定形状（大小约 [PATCH_CELL] 格），次层加一点边缘碎化。
     */
    private fun inPatch(x: Int, z: Int, salt: Long): Boolean {
        val wx = chunkX * 16 + x
        val wz = chunkZ * 16 + z
        val n = valueNoise(wx, wz, PATCH_CELL, salt + 500) * 0.78 +
                valueNoise(wx, wz, PATCH_CELL * 3, salt + 501) * 0.22
        return n * 100 < PATCH_CHANCE
    }

    /** 开口处的天花板透光：沿用 CorridorUtils（y6 灰色玻璃、y7 海晶灯）。 */
    private fun generateCeilingOpenings(chunkData: ChunkData, openings: Int) {
        val arr = BooleanArray(4)
        arr[0] = CorridorMaze.isOpen(openings, CorridorMaze.UP)
        arr[1] = CorridorMaze.isOpen(openings, CorridorMaze.DOWN)
        arr[2] = CorridorMaze.isOpen(openings, CorridorMaze.LEFT)
        arr[3] = CorridorMaze.isOpen(openings, CorridorMaze.RIGHT)
        CorridorUtils.generateCeilingOpenings(chunkData, arr)
    }

    // ==================== 地面 / 苔藓 / 藤蔓 / 地毯 ====================

    /**
     * 地面装饰，按预设分风格。花哨的苔藓/藤蔓只属于「藤蔓斑驳」预设。
     *
     * 地板**材质**补丁可以铺满整个区块（墙下面那部分反正看不见），
     * 但 y1 上的**立体装饰**（半砖、碎块、陶罐）只放在 [open]（真正能走的那块内部）里 ——
     * 放到墙带/门洞里会把通道堵窄，甚至把门堵死。
     */
    private fun generateFloor(chunkData: ChunkData, preset: CorridorPreset, open: IntRange) {
        when (preset) {
            CorridorPreset.NORMAL -> Unit

            // 苔藓块 + 苔藓毯
            CorridorPreset.OVERGROWN -> for (x in 0..15) {
                for (z in 0..15) {
                    if (hash(x, 0, z, SALT_MOSS) % 100 >= 16) continue
                    chunkData.setBlock(x, 0, z, Material.MOSS_BLOCK)
                    if (hash(x, 1, z, SALT_MOSS) % 100 < 50) {
                        chunkData.setBlock(x, 1, z, Material.MOSS_CARPET)
                    }
                }
            }

            // 石头地：补丁片里铺碎石/圆石，再散几块石头半砖
            CorridorPreset.STONE -> for (x in 0..15) {
                for (z in 0..15) {
                    if (!inPatch(x, z, SALT_MOSS)) continue
                    when (hash(x, 0, z, SALT_MOSS) % 3) {
                        0 -> chunkData.setBlock(x, 0, z, Material.COBBLESTONE)
                        1 -> chunkData.setBlock(x, 0, z, Material.GRAVEL)
                        else -> chunkData.setBlock(x, 0, z, Material.MOSSY_COBBLESTONE)
                    }
                    if (!doorFront[x][z] && x in open && z in open) {
                        if (hash(x, 3, z, SALT_MOSS) % 100 < 9) {
                            setSlab(chunkData, x, 1, z, Material.COBBLESTONE_SLAB)
                        } else if (hash(x, 4, z, SALT_MOSS) % 100 < 5) {
                            chunkData.setBlock(x, 1, z, Material.GRAVEL)     // 小石堆
                        }
                    }
                }
            }

            // 陶瓦地：补丁片里铺陶瓦，再躺几块掉下来的陶瓦半砖
            CorridorPreset.TERRACOTTA -> for (x in 0..15) {
                for (z in 0..15) {
                    if (!inPatch(x, z, SALT_MOSS)) continue
                    when (hash(x, 0, z, SALT_MOSS) % 4) {
                        0 -> chunkData.setBlock(x, 0, z, Material.TERRACOTTA)
                        1 -> chunkData.setBlock(x, 0, z, Material.LIGHT_GRAY_TERRACOTTA)
                        else -> chunkData.setBlock(x, 0, z, Material.WHITE_TERRACOTTA)
                    }
                    // 陶瓦没有半砖，就摆一块碎陶瓦 / 一个陶罐当装饰
                    if (!doorFront[x][z] && x in open && z in open && hash(x, 3, z, SALT_MOSS) % 100 < 10) {
                        chunkData.setBlock(
                            x, 1, z,
                            if (hash(x, 5, z, SALT_MOSS) % 2 == 0) Material.TERRACOTTA else Material.WHITE_TERRACOTTA
                        )
                    } else if (!doorFront[x][z] && x in open && z in open && hash(x, 6, z, SALT_MOSS) % 100 < 3) {
                        chunkData.setBlock(x, 1, z, Material.DECORATED_POT)
                    }
                }
            }

            // 石砖地：补丁片里换石砖，再散几块石砖半砖
            CorridorPreset.STONE_BRICKS -> for (x in 0..15) {
                for (z in 0..15) {
                    if (!inPatch(x, z, SALT_MOSS)) continue
                    chunkData.setBlock(
                        x, 0, z,
                        if (hash(x, 0, z, SALT_MOSS) % 3 == 0) Material.CRACKED_STONE_BRICKS else Material.STONE_BRICKS
                    )
                    if (!doorFront[x][z] && x in open && z in open && hash(x, 3, z, SALT_MOSS) % 100 < 10) {
                        setSlab(chunkData, x, 1, z, Material.STONE_BRICK_SLAB)
                    }
                }
            }

            // 泥土：补丁片被土盖住，上面才长草，土里再插点根土/枯木
            CorridorPreset.DIRT -> for (x in 0..15) {
                for (z in 0..15) {
                    if (!inPatch(x, z, SALT_MOSS)) continue
                    when (hash(x, 0, z, SALT_MOSS) % 100) {
                        in 0..39 -> chunkData.setBlock(x, 0, z, Material.GRASS_BLOCK)
                        in 40..69 -> chunkData.setBlock(x, 0, z, Material.DIRT)
                        in 70..87 -> chunkData.setBlock(x, 0, z, Material.COARSE_DIRT)
                        else -> chunkData.setBlock(x, 0, z, Material.ROOTED_DIRT)
                    }
                    if (doorFront[x][z] || x !in open || z !in open) continue
                    when (hash(x, 1, z, SALT_MOSS) % 100) {
                        in 0..24 -> {
                            // 高草丛两格高：上半截得用 half = TOP 单独放，不然只有底部
                            if (!chunkData.setDoublePlant(x, 1, z, Material.TALL_GRASS)) {
                                chunkData.setBlock(x, 1, z, Material.SHORT_GRASS)
                            }
                        }
                        in 25..33 -> chunkData.setBlock(x, 1, z, Material.FERN)
                        in 34..36 -> chunkData.setBlock(x, 1, z, Material.DEAD_BUSH)
                        37 -> chunkData.setBlock(x, 1, z, Material.BROWN_MUSHROOM)
                        38 -> chunkData.setBlock(x, 1, z, Material.RED_MUSHROOM)
                    }
                }
            }
        }
    }

    /** 藤蔓：贴着墙、从天花板往下挂。 */
    private fun generateVines(chunkData: ChunkData, open: IntRange) {
        // 沿四面墙的内侧找位置
        for (i in open) {
            tryVine(chunkData, open.first, i, attachWest = true)
            tryVine(chunkData, open.last, i, attachEast = true)
            tryVine(chunkData, i, open.first, attachNorth = true)
            tryVine(chunkData, i, open.last, attachSouth = true)
        }
    }

    private fun tryVine(
        chunkData: ChunkData,
        x: Int,
        z: Int,
        attachNorth: Boolean = false,
        attachSouth: Boolean = false,
        attachWest: Boolean = false,
        attachEast: Boolean = false
    ) {
        if (hash(x, 77, z, SALT_VINE) % 100 >= 22) return
        val length = 1 + hash(x, 78, z, SALT_VINE) % 4

        for (k in 0 until length) {
            val y = wallTopY - k
            if (y <= wallBottomY) break
            if (chunkData.getType(x, y, z) != Material.AIR) break
            chunkData.setBlock(x, y, z, vineData(attachNorth, attachSouth, attachWest, attachEast))
        }
    }

    /** 地板上的半砖（风格装饰用，比如「掉下来的陶瓦」）。 */
    private fun setSlab(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material) {
        val data = material.createBlockData() as Slab
        data.type = Slab.Type.BOTTOM
        chunkData.setBlock(x, y, z, data)
    }

    private fun vineData(        north: Boolean, south: Boolean, west: Boolean, east: Boolean
    ): MultipleFacing {
        val data = Material.VINE.createBlockData() as MultipleFacing
        data.setFace(BlockFace.NORTH, north)
        data.setFace(BlockFace.SOUTH, south)
        data.setFace(BlockFace.WEST, west)
        data.setFace(BlockFace.EAST, east)
        return data
    }

    /** 地毯：以 2×2 为单位成片铺（单格会像脏点），颜色随机。 */
    private fun generateCarpets(chunkData: ChunkData, open: IntRange) {
        for (x in open) {
            for (z in open) {
                if (chunkData.getType(x, 1, z) != Material.AIR) continue
                if (chunkData.getType(x, 0, z) == Material.AIR) continue
                // 2×2 网格用全局坐标索引，否则跨区块会错开
                val cellX = Math.floorDiv(chunkX * 16 + x, 2)
                val cellZ = Math.floorDiv(chunkZ * 16 + z, 2)
                if (rawHash(cellX, 5, cellZ, SALT_CARPET) % 100 >= 22) continue
                val color = carpets[rawHash(cellX, 6, cellZ, SALT_CARPET) % carpets.size]
                chunkData.setBlock(x, 1, z, color)
            }
        }
    }

    // ==================== 家具 ====================

    /** 按粗网格撒家具（确定性），只在活动区内、且位置是空的才放。 */
    private fun generateFurniture(chunkData: ChunkData, open: IntRange, preset: CorridorPreset) {
        val cell = 3
        var anchor = open.first
        while (anchor <= open.last) {
            var z = open.first
            while (z <= open.last) {
                tryFurniture(chunkData, anchor, z, open, preset)
                z += cell
            }
            anchor += cell
        }
    }

    private fun tryFurniture(chunkData: ChunkData, x: Int, z: Int, open: IntRange, preset: CorridorPreset) {
        val h = hash(x, 40, z, SALT_FURNITURE)
        if (h % 100 >= 30) return

        when ((h / 100) % 8) {
            0 -> placeBarrelStack(chunkData, x, z, h, open)
            1 -> placeChest(chunkData, x, z, h)
            2 -> placeCauldron(chunkData, x, z, h)
            3 -> placeLampPost(chunkData, x, z, h)
            4 -> placePlankPile(chunkData, x, z, h, open)
            5 -> placeRubblePile(chunkData, x, z, h, open, preset)
            6 -> placeCobwebs(chunkData, x, z, h, open)
            else -> placeCrateStack(chunkData, x, z, h)
        }
    }

    /**
     * 只在活动区内、该位置还是空气时才放。
     * @return 是否真的放下了（上层家具必须据此判断，否则会出现**悬空**的方块）
     */
    private fun setIfAir(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material): Boolean {
        if (x !in 0..15 || z !in 0..15) return false
        if (y !in 0..15) return false
        if (doorFront[x][z]) return false          // 门洞正前方不放家具，否则会把门堵死
        if (chunkData.getType(x, y, z) != Material.AIR) return false
        chunkData.setBlock(x, y, z, material)
        return true
    }

    private fun placeBarrelStack(chunkData: ChunkData, x: Int, z: Int, h: Int, open: IntRange) {
        val count = 1 + (h / 7) % 2
        for (i in 0 until count) {
            val nx = x + (h / (i * 13 + 3)) % 2
            val nz = z + (h / (i * 29 + 5)) % 2
            if (nx !in open || nz !in open) continue
            if (!setIfAir(chunkData, nx, 1, nz, Material.BARREL)) continue
            if ((h / 11) % 3 == 0) setIfAir(chunkData, nx, 2, nz, Material.BARREL)
        }
    }

    private fun placeChest(chunkData: ChunkData, x: Int, z: Int, h: Int) {
        if (!setIfAir(chunkData, x, 1, z, Material.CHEST)) return
        if (h % 5 == 0) setIfAir(chunkData, x, 2, z, Material.CHEST)
    }

    private fun placeCauldron(chunkData: ChunkData, x: Int, z: Int, h: Int) {
        setIfAir(chunkData, x, 1, z, if (h % 3 == 0) Material.CAULDRON else Material.COMPOSTER)
    }

    private fun placeLampPost(chunkData: ChunkData, x: Int, z: Int, h: Int) {
        if (!setIfAir(chunkData, x, 1, z, Material.STONE_BRICK_WALL)) return
        if (!setIfAir(chunkData, x, 2, z, Material.SPRUCE_FENCE)) return
        setIfAir(chunkData, x, 3, z, Material.LANTERN)
    }

    private fun placePlankPile(chunkData: ChunkData, x: Int, z: Int, h: Int, open: IntRange) {
        val alongX = h % 2 == 0
        val length = 2 + (h / 3) % 2
        val material = when ((h / 5) % 4) {
            0 -> Material.OAK_PLANKS
            1 -> Material.SPRUCE_PLANKS
            2 -> Material.DARK_OAK_PLANKS
            else -> Material.BIRCH_PLANKS
        }
        for (i in 0 until length) {
            val nx = if (alongX) x + i else x
            val nz = if (alongX) z else z + i
            if (nx !in open || nz !in open) continue
            if (!setIfAir(chunkData, nx, 1, nz, material)) continue
            if (h % 4 == 0) setIfAir(chunkData, nx, 2, nz, material)
        }
    }

    private fun placeRubblePile(chunkData: ChunkData, x: Int, z: Int, h: Int, open: IntRange, preset: CorridorPreset) {
        // 碎石堆跟着预设走：陶瓦段就是陶瓦渣、石头段是碎石、泥土段是土块……
        val material = when (preset) {
            CorridorPreset.TERRACOTTA -> when ((h / 9) % 3) {
                0 -> Material.TERRACOTTA
                1 -> Material.WHITE_TERRACOTTA
                else -> Material.LIGHT_GRAY_TERRACOTTA
            }

            CorridorPreset.STONE -> when ((h / 9) % 3) {
                0 -> Material.COBBLESTONE
                1 -> Material.STONE
                else -> Material.GRAVEL
            }

            CorridorPreset.STONE_BRICKS -> when ((h / 9) % 3) {
                0 -> Material.CRACKED_STONE_BRICKS
                1 -> Material.STONE_BRICKS
                else -> Material.MOSSY_STONE_BRICKS
            }

            CorridorPreset.DIRT -> when ((h / 9) % 3) {
                0 -> Material.DIRT
                1 -> Material.COARSE_DIRT
                else -> Material.PODZOL
            }

            else -> when ((h / 9) % 4) {
                0 -> Material.COBBLESTONE
                1 -> Material.MOSSY_COBBLESTONE
                2 -> Material.GRAVEL
                else -> Material.CRACKED_STONE_BRICKS
            }
        }
        for (dx in 0..1) {
            for (dz in 0..1) {
                val nx = x + dx
                val nz = z + dz
                if (nx !in open || nz !in open) continue
                if (hash(nx, 3, nz, SALT_FURNITURE) % 100 >= 70) continue
                if (!setIfAir(chunkData, nx, 1, nz, material)) continue
                if (hash(nx, 4, nz, SALT_FURNITURE) % 100 < 25) {
                    setIfAir(chunkData, nx, 2, nz, material)
                }
            }
        }
    }

    private fun placeCobwebs(chunkData: ChunkData, x: Int, z: Int, h: Int, open: IntRange) {
        for (dx in -1..1) {
            for (dz in -1..1) {
                val nx = x + dx
                val nz = z + dz
                if (nx !in open || nz !in open) continue
                if (hash(nx, 8, nz, SALT_FURNITURE) % 100 >= 45) continue
                val y = if (hash(nx, 9, nz, SALT_FURNITURE) % 2 == 0) 4 else 5
                setIfAir(chunkData, nx, y, nz, Material.COBWEB)
            }
        }
    }

    private fun placeCrateStack(chunkData: ChunkData, x: Int, z: Int, h: Int) {
        if (!setIfAir(chunkData, x, 1, z, Material.BARREL)) return
        if (!setIfAir(chunkData, x, 2, z, Material.BARREL)) return
        if (h % 3 == 0) setIfAir(chunkData, x, 3, z, Material.BARREL)
    }

    // ==================== 贴墙装饰 ====================

    /**
     * 贴墙装饰：一律摆在**贴着墙的那一格空气**里。
     *
     * 锚点是**世界坐标**上每 [WALL_DECOR_STEP] 格一个（东西向的墙沿 Z 数、南北向的沿 X 数），
     * 所以相邻区块会连成一条整齐的线 —— 每 4 格一个装饰位，而不是一块一块乱撒。
     *
     * 每个装饰位按哈希挑一样：
     *
     * | 权重 | 内容 |
     * | --- | --- |
     * | 28% | 壁挂火把（[Material.WALL_TORCH]，随预设偶尔换成灵魂/红石火把） |
     * | 8%  | 灵魂火把 [Material.SOUL_WALL_TORCH] |
     * | 7%  | 红石火把 [Material.REDSTONE_WALL_TORCH]（暗红，机房味） |
     * | 15% | **横向铁链管线**（贴着墙在 y4 拉 2~4 格，1/3 概率下面吊一盏灯） |
     * | 12% | 吊灯（y5 铁链 + y4 灯笼） |
     * | 5%  | 天花板铃铛 [Material.BELL]（贴在墙边挂着） |
     * | 9%  | 地面贴墙摆件：花盆 / 蜡烛 / 陶罐 |
     * | 7%  | 地面贴墙容器：木桶 / 箱子 / 炼药锅 / 堆肥桶 |
     * | 9%  | 空着（留白） |
     *
     * 地面那两类（有碰撞体积）只放在 [open] 内部，过道墙带只挂不挡路的东西。
     *
     * **朝向约定**：`facing` = 方块正面 = **从墙指向房间里**。
     * 原版墙上的火把就是这个规矩（挂在西边墙上时 `facing = EAST`，火苗朝屋里），
     * 所以这里「墙在西边 → facing = EAST」。
     *
     * 墙那一侧要是没有墙（过道那一侧是空的、门洞里被挖空了）就整个跳过，
     * 所以火把不会挂到门洞上，也不会挂在半空中。
     */
    private fun generateWallDecor(chunkData: ChunkData, open: IntRange, preset: CorridorPreset) {
        for (x in 1..14) {
            for (z in 1..14) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                // 东西两侧的墙（沿 Z 排布）→ 锚点沿 Z 数
                if (Math.floorMod(wz, WALL_DECOR_STEP) == WALL_DECOR_OFFSET) {
                    decorAgainstWall(chunkData, x, z, open, preset, -1, 0)
                    decorAgainstWall(chunkData, x, z, open, preset, 1, 0)
                }
                // 南北两侧的墙（沿 X 排布）→ 锚点沿 X 数
                if (Math.floorMod(wx, WALL_DECOR_STEP) == WALL_DECOR_OFFSET) {
                    decorAgainstWall(chunkData, x, z, open, preset, 0, -1)
                    decorAgainstWall(chunkData, x, z, open, preset, 0, 1)
                }
            }
        }
    }

    /**
     * 一个贴墙装饰位。[dx]/[dz] 指向**墙**那一侧。
     *
     * 有碰撞体积的（花盆 / 容器 / 蜡烛）只放在 [open]（真正的内部空间）里，
     * 不放到过道墙带上去 —— 那里是走道和门口，摆个木桶会把通道堵窄甚至把门口堵死
     * （探针 PassageProbe 会报 `unreachable door sills` / `band cells not walkable`）。
     */
    private fun decorAgainstWall(
        chunkData: ChunkData,
        x: Int,
        z: Int,
        open: IntRange,
        preset: CorridorPreset,
        dx: Int,
        dz: Int
    ) {
        val wallX = x + dx
        val wallZ = z + dz
        // 那一侧必须真的有墙。门洞是把墙挖空的（只挖到 y3），所以在 y3 探墙正好把门洞排掉；
        // 过道那一侧整条墙带是空的，也同样被排掉。
        if (!isWallAt(chunkData, wallX, WALL_DECOR_PROBE_Y, wallZ)) return
        // 门洞正前方那一条线也留空，别把门口堵成杂物角
        if (doorFront[x][z]) return
        // 贴墙这一格本身要空着（有人先占了就跳过）
        if (!isAirAt(chunkData, x, WALL_DECOR_PROBE_Y, z)) return

        // facing = 从墙指向房间里
        val facing = when {
            dx < 0 -> BlockFace.EAST
            dx > 0 -> BlockFace.WEST
            dz < 0 -> BlockFace.SOUTH
            else -> BlockFace.NORTH
        }
        // 铁链管线要顺着墙走：墙是东西向的（dx != 0），链子就沿 Z
        val alongWall = if (dx != 0) Axis.Z else Axis.X

        val h = hash(x, WALL_DECOR_PROBE_Y, z, SALT_WALL_DECOR)
        val sh = hash(x, WALL_DECOR_PROBE_Y + 1, z, SALT_WALL_DECOR)
        val ty = if (h % 3 == 0) 4 else 3

        when (h % 100) {
            in 0..27 -> setWallMounted(chunkData, x, ty, z, wallTorchFor(preset, h), facing)
            in 28..35 -> setWallMounted(chunkData, x, ty, z, Material.SOUL_WALL_TORCH, facing)
            in 36..42 -> setWallMounted(chunkData, x, ty, z, Material.REDSTONE_WALL_TORCH, facing)

            // 横向铁链「管线」：顺着墙拉 2~4 格，1/3 概率在起点下面吊一盏灯
            in 43..57 -> {
                val length = 2 + (h / 7) % 3
                val stepX = if (dx != 0) 0 else 1
                val stepZ = if (dx != 0) 1 else 0
                var placed = 0
                for (i in 0 until length) {
                    val cx = x + stepX * i
                    val cz = z + stepZ * i
                    // 每一节都得还贴着墙：墙断了（门洞、拐角、墙到头了）就停下，
                    // 否则链子会有一截悬在没有墙的地方
                    if (!isWallAt(chunkData, cx + dx, WALL_DECOR_CHAIN_Y, cz + dz)) break
                    if (!setChain(chunkData, cx, WALL_DECOR_CHAIN_Y, cz, alongWall)) break
                    placed++
                }
                if (placed > 0 && sh % 3 == 0) {
                    setHangingLantern(
                        chunkData, x, WALL_DECOR_CHAIN_Y - 1, z, lanternFor(preset, sh)
                    )
                }
            }

            // 吊灯：天花板下一小节链子 + 灯笼
            in 58..69 -> {
                if (setChain(chunkData, x, WALL_DECOR_PROBE_Y + 2, z, Axis.Y)) {
                    setHangingLantern(
                        chunkData, x, WALL_DECOR_PROBE_Y + 1, z, lanternFor(preset, sh)
                    )
                }
            }

            // 天花板铃铛（贴在墙边挂着）
            in 70..74 -> setCeilingBell(chunkData, x, WALL_DECOR_PROBE_Y + 2, z)

            // 地面贴墙摆件（有碰撞体积，只放内部空间）
            in 75..83 -> if (x in open && z in open) {
                when ((h / 11) % 3) {
                    0 -> setFlowerPot(chunkData, x, z, preset, h)
                    1 -> setCandles(chunkData, x, z, 1 + (h / 13) % 4)
                    else -> setIfAir(chunkData, x, 1, z, Material.DECORATED_POT)
                }
            }

            // 地面贴墙容器（同上，只放内部空间）
            in 84..90 -> if (x in open && z in open) {
                setIfAir(chunkData, x, 1, z, containerFor(h))
            }

            else -> Unit
        }
    }

    /** 壁挂火把：脏/荒的预设更容易点灵魂火把（冷光），其余就是普通火把。 */
    private fun wallTorchFor(preset: CorridorPreset, h: Int): Material = when (preset) {
        CorridorPreset.DIRT, CorridorPreset.OVERGROWN ->
            if (h % 3 == 0) Material.SOUL_WALL_TORCH else Material.WALL_TORCH

        else -> Material.WALL_TORCH
    }

    private fun lanternFor(preset: CorridorPreset, h: Int): Material =
        if (preset == CorridorPreset.DIRT || preset == CorridorPreset.OVERGROWN) {
            if (h % 2 == 0) Material.SOUL_LANTERN else Material.LANTERN
        } else {
            Material.LANTERN
        }

    private fun containerFor(h: Int): Material = when ((h / 17) % 5) {
        0 -> Material.BARREL
        1 -> Material.CHEST
        2 -> Material.CAULDRON
        3 -> Material.COMPOSTER
        else -> Material.TRAPPED_CHEST
    }

    /** 花盆：脏预设里是蕨 / 枯木 / 蘑菇，干净的地方是花草。 */
    private fun setFlowerPot(
        chunkData: ChunkData,
        x: Int,
        z: Int,
        preset: CorridorPreset,
        h: Int
    ): Boolean {
        val pots = if (preset == CorridorPreset.DIRT || preset == CorridorPreset.OVERGROWN) {
            WILD_POTS
        } else {
            TIDY_POTS
        }
        return setIfAir(chunkData, x, 1, z, pots[(h / 5) % pots.size])
    }

    /** 蜡烛：1~4 根一簇，直接点亮。 */
    private fun setCandles(chunkData: ChunkData, x: Int, z: Int, count: Int): Boolean {
        if (!isAirAt(chunkData, x, 1, z)) return false
        if (doorFront[x][z]) return false
        val data = Material.CANDLE.createBlockData() as Candle
        data.candles = count.coerceIn(1, 4)
        data.isLit = true
        chunkData.setBlock(x, 1, z, data)
        return true
    }

    /**
     * 壁挂火把一类（贴在墙上、有正面朝向）的方块。
     * `facing` = 正面朝向 = 从墙指向房间里。
     */
    private fun setWallMounted(
        chunkData: ChunkData,
        x: Int,
        y: Int,
        z: Int,
        material: Material,
        facing: BlockFace
    ): Boolean {
        if (!isAirAt(chunkData, x, y, z)) return false
        val data = material.createBlockData() as Directional
        data.facing = facing
        chunkData.setBlock(x, y, z, data)
        return true
    }

    /** 铁链：横向的一节就是「管线」，`axis` 决定它横在哪个方向。 */
    private fun setChain(chunkData: ChunkData, x: Int, y: Int, z: Int, axis: Axis): Boolean {
        if (x !in 1..14 || z !in 1..14) return false
        if (!isAirAt(chunkData, x, y, z)) return false
        val data = Material.IRON_CHAIN.createBlockData() as Orientable
        data.axis = axis
        chunkData.setBlock(x, y, z, data)
        return true
    }

    /** 吊灯：`hanging = true`，不然默认是「放在地上」的灯笼，挂在天花板下就是浮着的。 */
    private fun setHangingLantern(
        chunkData: ChunkData,
        x: Int,
        y: Int,
        z: Int,
        material: Material
    ): Boolean {
        if (!isAirAt(chunkData, x, y, z)) return false
        val data = material.createBlockData() as Lantern
        data.isHanging = true
        chunkData.setBlock(x, y, z, data)
        return true
    }

    /** 天花板铃铛。 */
    private fun setCeilingBell(chunkData: ChunkData, x: Int, y: Int, z: Int): Boolean {
        if (!isAirAt(chunkData, x, y, z)) return false
        val data = Material.BELL.createBlockData() as Bell
        data.attachment = Bell.Attachment.CEILING
        data.facing = BlockFace.NORTH
        chunkData.setBlock(x, y, z, data)
        return true
    }

    private fun isAirAt(chunkData: ChunkData, x: Int, y: Int, z: Int): Boolean =
        x in 0..15 && z in 0..15 && y in 0..255 && chunkData.getType(x, y, z) == Material.AIR

    /**
     * 这一格算不算「墙」。
     *
     * 这里用**白名单**（走廊自己砌墙用的那些方块），而不是 `Material.isOccluding()`：
     * 一来 Paper 的 `isOccluding()` 要查注册表（无头环境跑不了，探针没法验证），
     * 二来白名单更准 —— 铁栏杆、藤蔓、家具都不算墙，火把不会挂到它们身上。
     * 万一漏了哪种墙材质，结果只是那面墙少挂一个火把，不会出怪东西。
     */
    private fun isWallAt(chunkData: ChunkData, x: Int, y: Int, z: Int): Boolean {
        if (x !in 0..15 || z !in 0..15) return false
        return WALL_MATERIALS.contains(chunkData.getType(x, y, z))
    }

    // ==================== 房间内部 ====================

    /** 房间：四角灯柱 + 中央矮台（地毯由 generateCarpets 铺）。 */
    private fun generateRoomInterior(chunkData: ChunkData, open: IntRange) {
        val c = (open.first + open.last) / 2
        for (dx in -1..0) {
            for (dz in -1..0) {
                setIfAir(chunkData, c + dx, 1, c + dz, Material.STONE_BRICKS)
            }
        }
        for ((x, z) in listOf(open.first to open.first, open.first to open.last,
            open.last to open.first, open.last to open.last)) {
            if (!setIfAir(chunkData, x, 1, z, Material.STONE_BRICK_WALL)) continue
            if (!setIfAir(chunkData, x, 2, z, Material.STONE_BRICK_WALL)) continue
            setIfAir(chunkData, x, 3, z, Material.LANTERN)
        }
    }

    /** 尽端小间：一盏灯 + 一点物资。 */
    private fun generateDeadEndInterior(chunkData: ChunkData, open: IntRange) {
        val c = (open.first + open.last) / 2
        if (setIfAir(chunkData, c, 1, c, Material.STONE_BRICKS)) {
            setIfAir(chunkData, c, 2, c, Material.LANTERN)
        }
        setIfAir(chunkData, open.first, 1, open.first, Material.CHEST)
        setIfAir(chunkData, open.last, 1, open.last, Material.BARREL)
        if (open.last - open.first >= 3) {
            setIfAir(chunkData, open.first, 1, open.last, Material.COBWEB)
        }
    }

    // ==================== 地层 ====================

    private fun generateFoundation(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()

        for (y in WORLD_FLOOR_MIX_Y..-2) fillLayerDeepslateMixed(chunkData, y)

        fillLayerStoneMixed(chunkData, -1)
        fillLayerStoneMixed(chunkData, 0)

        for (y in 8..15) chunkData.fillLayer(y, Material.POLISHED_TUFF)
    }

    /**
     * 天花板
     * y6: 闪长岩（中间 2x2 灰色玻璃）
     * y7: 海晶灯 / 荧石
     */
    private fun generateCeiling(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                chunkData.setBlock(x, 6, z, Material.DIORITE)
            }
        }

        val centerX = 8
        val centerZ = 8
        for (x in centerX - 1..centerX) {
            for (z in centerZ - 1..centerZ) {
                chunkData.setBlock(x, 6, z, Material.GRAY_STAINED_GLASS)
            }
        }

        for (x in 0..15) {
            for (z in 0..15) {
                // 按位置哈希取灯的种类（原来是 taboolib 的全局随机，区块会不自洽）
                val light = if (hash(x, 7, z, SALT_LIGHT) % 3 == 0) Material.SEA_LANTERN else Material.GLOWSTONE
                chunkData.setBlock(x, 7, z, light)
            }
        }
    }

    private fun fillLayerStoneMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) {
            for (z in 0..15) {
                chunkData.setBlock(x, y, z, pickWeighted(STONE_MIX, stoneWeights, hash(x, y, z, SALT_STONE)))
            }
        }
    }

    private fun fillLayerDeepslateMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) {
            for (z in 0..15) {
                chunkData.setBlock(
                    x, y, z,
                    pickWeighted(DEEPSLATE_MIX, deepslateWeights, hash(x, y, z, SALT_STONE + 1))
                )
            }
        }
    }

    /**
     * 按权重挑材质，但用**传进来的哈希**决定，不用全局随机。
     *
     * 这里原来是 `ChunkGen.weightedRandom(...)`（走 taboolib 的全局随机），
     * 于是同一区块生成两次，y-11~0 那十几层混合材质的图案每次都不一样 —— 不符合本工程
     * 「一切都由位置哈希决定」的约定（探针里表现为 `same chunk twice, diff=1887`）。
     */
    private fun pickWeighted(materials: List<Material>, weights: List<Int>, h: Int): Material {
        var roll = h % weights.sum()
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll < 0) return materials[i]
        }
        return materials.first()
    }

    // ==================== 确定性哈希 ====================

    /**
     * 按区块内局部坐标哈希（内部会换算成世界坐标，所以同一个方块在哪个区块里算都一样）。
     * [x]/[z] 必须是 0..15 的局部坐标。
     */
    private fun hash(x: Int, y: Int, z: Int, salt: Long): Int {
        var h = worldSeed xor salt
        h = h * 6364136223846793005L + chunkX * 16 + x
        h = h * 6364136223846793005L + y
        h = h * 6364136223846793005L + chunkZ * 16 + z
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return (h and 0x7FFFFFFF).toInt()
    }

    /**
     * 只按传入的三个数哈希，不再叠加区块坐标。
     * 用于「坐标本身已经是全局量」的场景（噪声格点、地毯网格等），否则会跨区块错开。
     */
    private fun rawHash(a: Int, b: Int, c: Int, salt: Long): Int {
        var h = worldSeed xor salt
        h = h * 6364136223846793005L + a
        h = h * 6364136223846793005L + b
        h = h * 6364136223846793005L + c
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return (h and 0x7FFFFFFF).toInt()
    }

    companion object {
        private const val SALT_WALL = 5201L
        private const val SALT_MOSS = 5202L
        private const val SALT_VINE = 5203L
        private const val SALT_CARPET = 5204L
        private const val SALT_FURNITURE = 5205L
        private const val SALT_LIGHT = 5206L
        private const val SALT_PRESET = 5207L
        private const val SALT_WALL_DECOR = 5208L
        private const val SALT_STONE = 5209L

        /** 地下混合层用的材质（y-11~0），由 [pickWeighted] + 位置哈希挑。 */
        private val STONE_MIX = listOf(Material.STONE, Material.ANDESITE, Material.GRAVEL)
        private val DEEPSLATE_MIX =
            listOf(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE_BRICKS)

        /** 贴墙装饰的间距 / 相位（沿墙每 4 格一个装饰位，跨区块对齐）。 */
        private const val WALL_DECOR_STEP = 4
        private const val WALL_DECOR_OFFSET = 1

        /**
         * 探「那一侧是不是墙」的高度。
         * 门洞只挖到 y3（`RegionGateway.PASSAGE_BOTTOM=1` + 高度 3），所以站在 y3 探墙，
         * 门洞那一列自然探不到墙，火把就不会挂到门洞里。
         */
        private const val WALL_DECOR_PROBE_Y = 3

        /** 横向铁链（管线）的高度。 */
        private const val WALL_DECOR_CHAIN_Y = 4

        /** 花盆：干净的地方摆花草。 */
        private val TIDY_POTS = listOf(
            Material.POTTED_OAK_SAPLING,
            Material.POTTED_POPPY,
            Material.POTTED_DANDELION,
            Material.POTTED_CORNFLOWER,
            Material.POTTED_AZALEA_BUSH,
            Material.POTTED_BLUE_ORCHID
        )

        /** 花盆：荒掉的地方摆蕨 / 枯木 / 蘑菇。 */
        private val WILD_POTS = listOf(
            Material.POTTED_FERN,
            Material.POTTED_DEAD_BUSH,
            Material.POTTED_RED_MUSHROOM,
            Material.POTTED_BROWN_MUSHROOM,
            Material.POTTED_CACTUS
        )

        /**
         * 「能挂东西的墙」白名单：走廊（含区域交界、过道那一侧的边墙、风格补丁）砌墙会用到的方块。
         * 不算墙的一律不挂（铁栏杆、藤蔓、家具、门、玻璃、半砖……）。
         */
        private val WALL_MATERIALS = setOf(
            // 普通走廊的底色墙
            Material.POLISHED_DIORITE,
            Material.WHITE_CONCRETE,
            Material.GRAY_CONCRETE,
            // 藤蔓斑驳预设掺进去的砖石
            Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE,
            Material.CRACKED_STONE_BRICKS,
            // 风格补丁（陶瓦 / 石头 / 石砖 / 泥土）
            Material.STONE,
            Material.ANDESITE,
            Material.GRAVEL,
            Material.STONE_BRICKS,
            Material.MOSSY_STONE_BRICKS,
            Material.CHISELED_STONE_BRICKS,
            Material.TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA,
            Material.WHITE_TERRACOTTA,
            Material.BROWN_TERRACOTTA,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.ROOTED_DIRT,
            Material.MOSS_BLOCK,
            // 别的段 / 区域交界的墙也算，别的地方掉进来的墙也认得出来
            Material.DEEPSLATE_BRICKS,
            Material.POLISHED_TUFF,
            Material.TUFF_BRICKS,
            Material.SMOOTH_SANDSTONE,
            Material.CYAN_TERRACOTTA,
            Material.CYAN_GLAZED_TERRACOTTA,
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.DARK_OAK_PLANKS,
            Material.BIRCH_PLANKS,
            Material.STRIPPED_OAK_LOG,
            Material.STRIPPED_SPRUCE_LOG
        )

        /**
         * 预设噪声的格点大小（方块）。必须**远大于 16**，否则相邻区块的采样点落在不同格点里，
         * 风格会变成一片花斑（实测 16 左右时"相邻区块同风格"只有 ~32%，等于纯随机）。
         */
        private const val PRESET_CELL = 120

        /**
         * 主题预设的补丁比例（%）：底色永远是普通走廊那道墙 / 地面，只有这些比例的小片换成风格材质。
         * 形状由平滑噪声画出来（[PATCH_CELL] 格一层），所以是圆滑的一小片，不是 3×3 方块。
         */
        private const val PATCH_CHANCE = 38

        /** 风格补丁的噪声格点大小（方块）：越大片越大越圆。 */
        private const val PATCH_CELL = 9
    }
}
