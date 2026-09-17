package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_FLOOR_MIX_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.fillLayer
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.setDoublePlant
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionBoundary
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionGateway
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.MultipleFacing
import org.bukkit.block.data.type.Leaves
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Stairs
import org.bukkit.generator.ChunkGenerator.ChunkData

/**
 * GL1PlainGarden - 丛林花园生成器（`RegionType.GARDEN`）
 *
 * 一片开阔的室内花园：草地 + 草丛 + 杜鹃花丛 + 几棵杜鹃树，四周和中间零星分布
 * 「跨区块的墙」与 3×3 的砂岩柱子，顶上盖一层浅绿/深绿玻璃，再往上封屏障。
 *
 * ## 层级
 * ```
 * y = 21       屏障（封顶）
 * y = 20       浅绿玻璃 / 深绿玻璃 / 空气（随机，相当于玻璃天窗）
 * y = 10 ~ 19  去皮白桦木（y10 以上全是）
 * y = 5 ~ 9    淡灰色混凝土粉末 / 圆石 / 苔藓圆石 + 去皮白桦木（越高概率越大）
 * y = 1 ~ 4    砂岩 / 平滑砂岩 / 白桦木板 / 绿色混凝土 / 苔藓块 / 圆石 / 苔藓圆石
 * y = 0        草方块（地板）
 * y = -1      石头混合
 * y = -15 ~ -2 深板岩混合
 * y = -16      基岩
 * ```
 * 墙高 20（y1~y19 是墙体，y20 是压在墙顶上的玻璃层）。
 *
 * ## 结构
 * - **区域边界墙**：邻居 [RegionBoundary] 是别的区域时砌一道墙，门洞由 [RegionGateway] 留空 + 装门；
 * - **跨区块的墙**：按「区块 = 一格」的全局网格撒，墙线落在区块边界上，
 *   相邻区块各自决定自己那 16 格 → 连起来就是立到好几个区块长墙（也因此不会在区块接缝处错开）；
 * - **3×3 柱子**：每格按哈希决定放不放、放在哪、用哪套配方（砂岩系 / 苔石系 / 白桦系 / 高塔）；
 *   有些区块两者都不生成，就是一片纯草地；
 * - **踢脚线**：贴着墙 / 柱子的一格 y1 是 [Material.SMOOTH_SANDSTONE_STAIRS]，
 *   **完整的那一面朝墙**（楼梯的 facing 就是「高的那一面」，所以 facing 指向墙）；
 *   柱子 / 墙拐角上斜着的那四个角也会补上一格（否则角上会缺一块）。
 * - **藤蔓 / 树叶**：贴区域墙内侧的一格从 y2 往上长藤蔓与橡树树叶，墙和柱子周围也会挂一些，
 *   用来模拟粗藤蔓。
 *
 * 所有随机都是**按位置哈希**（[hash] / [rawHash]）取的，同一个区块每次生成都一样。
 */
class GL1PlainGarden(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    // ==================== 尺寸 ====================

    /** 地板层（草方块）。 */
    private val groundY = 0

    /** 墙体最低层。 */
    private val wallBottomY = 1

    /** 墙体最高层。 */
    private val wallTopY = 19

    /** 玻璃天窗层（压在墙顶）。 */
    private val glassY = 20

    /** 屏障封顶层。 */
    private val roofY = 21

    /** 这一格里哪些 (x,z) 是墙 / 柱子（用来撒踢脚线、藤蔓、植物）。 */
    private val wallCells = Array(16) { BooleanArray(16) }

    /** 每一格踢脚线的朝向（null = 这格没有踢脚线），决定 shape 时要用邻居的这一份。 */
    private val baseboardFacing = Array(16) { arrayOfNulls<BlockFace>(16) }

    /** 四面区域交界的方案（N/S/W/E），[generate] 里算一次，后面装门、藤蔓、避让门洞都要用。 */
    private var plans: Array<RegionGateway.Plan?> = arrayOfNulls(4)

    // ==================== 主流程 ====================

    override fun generate(chunkData: ChunkData) {
        // 1. 地层 + 草地
        generateFoundation(chunkData)

        // 2. 区域边界墙（门洞留空）
        val plans = generateRegionWalls(chunkData)
        this.plans = plans

        // 2.5 记下门洞本身和门洞正前方那一格：所有装饰都要避开，不然门口会被楼梯/草丛堵住
        markPassageCells(plans)

        // 3. 跨区块的墙 + 3x3 柱子
        generateInternalStructures(chunkData, plans)

        // 4. 玻璃天窗 + 屏障
        generateCeiling(chunkData)

        // 5. 踢脚线 / 藤蔓 / 树叶（扫描上面记下来的 wallCells）
        generateWallDecor(chunkData)

        // 6. 草地上的草丛、杜鹃花丛、杜鹃树
        generateGround(chunkData)

        // 7. 门（最后放，免得被装饰挡住）
        RegionGateway.placeDoors(chunkData, plans[0])
        RegionGateway.placeDoors(chunkData, plans[1])
        RegionGateway.placeDoors(chunkData, plans[2])
        RegionGateway.placeDoors(chunkData, plans[3])
    }

    /** 门洞正前方那一格（踢脚线要避开，否则门口会卡一级楼梯）。 */
    private val doorFront = Array(16) { BooleanArray(16) }

    /** 门洞本身那几格（墙被挖掉的位置，不能放任何装饰）。 */
    private val opening = Array(16) { BooleanArray(16) }

    private fun markPassageCells(plans: Array<RegionGateway.Plan?>) {
        for (side in 0..3) {
            val plan = plans[side] ?: continue
            for (i in plan.gapStart until plan.gapEnd) {
                if (i !in 0..15) continue
                when (side) {
                    0 -> { doorFront[i][1] = true; opening[i][0] = true }
                    1 -> { doorFront[i][14] = true; opening[i][15] = true }
                    2 -> { doorFront[1][i] = true; opening[0][i] = true }
                    else -> { doorFront[14][i] = true; opening[15][i] = true }
                }
            }
        }
    }

    // ==================== 地层 ====================

    private fun generateFoundation(chunkData: ChunkData) {
        // 花园只铺底层；顶层那四层 + 基岩它不铺（它自己用玻璃 + 屏障封顶）
        chunkData.fillWorldFloor()

        for (y in WORLD_FLOOR_MIX_Y..-2) fillLayerMixed(chunkData, y, deepslateMix, deepslateWeights)

        // y=-1 垫石头，y=0 是草地
        fillLayerMixed(chunkData, -1, stoneMix, stoneWeights)
        chunkData.fillLayer(groundY, Material.GRASS_BLOCK)
    }

    // ==================== 区域边界墙 ====================

    /**
     * 邻居区域类型不同的那一侧砌墙；门 / 洞口按 [RegionGateway] 的方案留空（不是事后去挖）。
     * 返回四面各自的方案，后面的装门与藤蔓都要用。
     *
     * ⚠️ **只有 `planFor` 不为 null 的那一侧才砌墙**：`planFor` 只在邻居是**别的**区域时才给方案，
     * 所以「花园挨着花园」的那一侧是敞开的（花园之间连通）。
     * 之前这里四个方向无条件砌墙，于是每个区块之间都有一道墙。
     */
    private fun generateRegionWalls(chunkData: ChunkData): Array<RegionGateway.Plan?> {
        val plans = arrayOf(
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.NORTH),
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.SOUTH),
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.WEST),
            RegionGateway.planFor(chunkX, chunkZ, worldSeed, RegionBoundary.EAST)
        )

        for (i in 0..15) {
            for (y in wallBottomY..wallTopY) {
                if (plans[0] != null && !RegionGateway.opensAt(plans[0], i, y)) {
                    setWall(chunkData, i, y, 0, wallMaterialFor(i, y, 0))
                }
                if (plans[1] != null && !RegionGateway.opensAt(plans[1], i, y)) {
                    setWall(chunkData, i, y, 15, wallMaterialFor(i, y, 15))
                }
            }
        }
        for (i in 0..15) {
            for (y in wallBottomY..wallTopY) {
                if (plans[2] != null && !RegionGateway.opensAt(plans[2], i, y)) {
                    setWall(chunkData, 0, y, i, wallMaterialFor(0, y, i))
                }
                if (plans[3] != null && !RegionGateway.opensAt(plans[3], i, y)) {
                    setWall(chunkData, 15, y, i, wallMaterialFor(15, y, i))
                }
            }
        }
        return plans
    }

    /**
     * 墙体材质（按高度分层）。
     * - y >= 10：全是去皮白桦木
     * - y 5..9：中部混合（去皮白桦木概率随高度上升），并且和底部在 y5~7 有一半概率重叠
     * - y <= 4：底部七种砂岩系混合
     */
    private fun wallMaterialFor(x: Int, y: Int, z: Int): Material {
        if (y >= 10) return Material.STRIPPED_BIRCH_LOG

        if (y >= 5) {
            // 「部分区域和底部重叠」
            if (y <= 7 && hash(x, y + 11, z, SALT_WALL) % 100 < 45) return bottomMaterial(x, y, z)

            val birchChance = when (y) {
                5 -> 20
                6 -> 35
                7 -> 50
                8 -> 70
                else -> 88
            }
            if (hash(x, y + 40, z, SALT_WALL) % 100 < birchChance) return Material.STRIPPED_BIRCH_LOG
            return when (hash(x, y + 80, z, SALT_WALL) % 3) {
                0 -> Material.LIGHT_GRAY_CONCRETE_POWDER
                1 -> Material.COBBLESTONE
                else -> Material.MOSSY_COBBLESTONE
            }
        }

        return bottomMaterial(x, y, z)
    }

    /** 底部七种：砂岩、平滑砂岩、白桦木板、绿色混凝土、苔藓块、圆石、苔藓圆石。 */
    private fun bottomMaterial(x: Int, y: Int, z: Int): Material =
        bottomMix[hash(x, y + 7, z, SALT_WALL) % bottomMix.size]

    // ==================== 跨区块的墙 / 3x3 柱子 ====================

    /**
     * 每个区块算「一格」：
     * - 30% 只出墙、25% 只出柱子、15% 两者都有、30% 什么都不出（纯草地）；
     * - 墙线**不落在区块边界上**，而是落在「两个区块中间那条线」（局部 x=8 / z=8，
     *   即世界坐标 x ≡ 8 (mod 32)）—— 这样花园区块之间不会到处是墙，走得过人；
     * - 一条墙线按 32 格（2 个区块）为一段决定有没有，所以相邻区块拼起来是一条
     *   跨区块的连续长墙，且接缝两侧必然一致。
     */
    private fun generateInternalStructures(chunkData: ChunkData, plans: Array<RegionGateway.Plan?>) {
        val roll = rawHash(chunkX, 100, chunkZ, SALT_CELL) % 100
        val wantWalls = roll < 30 || roll in 55..69
        val wantPillar = roll in 30..54 || roll in 55..69

        if (wantWalls) {
            // 竖线：只在偶数 chunkX 的中间那一列（世界 x ≡ 8 mod 32），z 方向按 2 区块一段
            if (Math.floorMod(chunkX, 2) == 0) {
                val segZ = Math.floorDiv(chunkZ, 2)
                if (rawHash(Math.floorDiv(chunkX, 2), segZ, 0, SALT_LINE_V) % 100 < LINE_CHANCE) {
                    // 两端分别撞北/南区域墙，只有那两格才需要避让门洞
                    buildWallLine(chunkData, fixed = 8, alongZ = true, plansA = plans[0], plansB = plans[1])
                }
            }
            // 横线：只在偶数 chunkZ 的中间那一行
            if (Math.floorMod(chunkZ, 2) == 0) {
                val segX = Math.floorDiv(chunkX, 2)
                if (rawHash(segX, Math.floorDiv(chunkZ, 2), 1, SALT_LINE_H) % 100 < LINE_CHANCE) {
                    buildWallLine(chunkData, fixed = 8, alongZ = false, plansA = plans[2], plansB = plans[3])
                }
            }
        }

        if (wantPillar) buildPillar(chunkData)
    }

    /**
     * 一道 16 格长的墙（沿区块中间那条线走）。
     *
     * **材质是纯平滑砂岩**：这就是花园里的「一般墙壁」（非区域边界的那一种）。
     * 用区域墙那套分层做旧材质会让它看着像个斑驳的隔断，和踢脚线也搭不上。
     *
     * ⚠️ 门洞避让**只对墙线真正撞上区域墙的那一格生效**（i==0 用北/西方案、i==15 用南/东方案）。
     * 早先写成「整条线都拿 fixed 去查门洞」，于是只要两端任一侧有门，
     * 整条墙线的 y1~y2 全被跳过 —— 墙像浮在半空、踢脚线也跟着断。
     *
     * @param alongZ true = 墙沿 Z 方向（固定 x = [fixed]，两端撞北/南区域墙）；
     *               false = 沿 X 方向（固定 z = [fixed]，两端撞西/东区域墙）
     * @param plansA 低坐标那一端的区域墙方案（北 / 西），null = 那侧不是边界
     * @param plansB 高坐标那一端的区域墙方案（南 / 东），null = 那侧不是边界
     */
    private fun buildWallLine(
        chunkData: ChunkData,
        fixed: Int,
        alongZ: Boolean,
        plansA: RegionGateway.Plan?,
        plansB: RegionGateway.Plan?
    ) {
        for (i in 0..15) {
            for (y in wallBottomY..wallTopY) {
                if (i == 0 && RegionGateway.opensAt(plansA, fixed, y)) continue
                if (i == 15 && RegionGateway.opensAt(plansB, fixed, y)) continue
                // 一般墙壁（非区域边界）统一用纯平滑砂岩，别用那套分层做旧材质
                if (alongZ) setWall(chunkData, fixed, y, i, Material.SMOOTH_SANDSTONE)
                else setWall(chunkData, i, y, fixed, Material.SMOOTH_SANDSTONE)
            }
        }
    }

    private fun buildPillar(chunkData: ChunkData) {
        val px = 4 + rawHash(chunkX, 103, chunkZ, SALT_PILLAR) % 8     // 4..11
        val pz = 4 + rawHash(chunkX, 104, chunkZ, SALT_PILLAR) % 8
        val type = rawHash(chunkX, 105, chunkZ, SALT_PILLAR) % PILLAR_TYPES

        when (type) {
            // 切制砂岩塔（示例配方）
            0 -> {
                for (y in 1..4) fillPillarLayer(chunkData, px, pz, y, cutSandstoneMix())
                for (y in 5..9) fillPillarLayer(chunkData, px, pz, y, Material.SANDSTONE)
                for (y in 10..13) fillPillarLayer(chunkData, px, pz, y, Material.SANDSTONE_WALL)
                buildSpire(chunkData, px, pz, 14)
                buildSpireTip(chunkData, px, pz, 16)
            }

            // 平滑砂岩方柱
            1 -> {
                for (y in 1..6) fillPillarLayer(chunkData, px, pz, y, Material.SMOOTH_SANDSTONE)
                for (y in 7..10) fillPillarLayer(chunkData, px, pz, y, Material.CUT_SANDSTONE)
                for (y in 11..14) fillPillarLayer(chunkData, px, pz, y, Material.SANDSTONE_WALL)
                buildSpire(chunkData, px, pz, 15)
            }

            // 苔藓遗迹柱
            2 -> {
                for (y in 1..3) {
                    for (dx in 0..2) {
                        for (dz in 0..2) {
                            val m = if ((dx + dz + y) % 2 == 0) Material.MOSSY_COBBLESTONE else Material.COBBLESTONE
                            setWall(chunkData, px + dx, y, pz + dz, m)
                        }
                    }
                }
                for (y in 4..7) fillPillarLayer(chunkData, px, pz, y, Material.COBBLESTONE_WALL)
                for (y in 8..9) fillPillarLayer(chunkData, px, pz, y, Material.MOSS_BLOCK)
                for (dx in -1..3) {
                    for (dz in -1..3) {
                        if (dx in 0..2 && dz in 0..2) continue
                        if ((dx + dz) % 2 != 0) continue
                        setLeavesIfAir(chunkData, px + dx, 10, pz + dz, Material.AZALEA_LEAVES)
                    }
                }
            }

            // 白桦木柱
            3 -> {
                for (y in 1..8) fillPillarLayer(chunkData, px, pz, y, Material.STRIPPED_BIRCH_LOG)
                for (y in 9..10) fillPillarLayer(chunkData, px, pz, y, Material.BIRCH_PLANKS)
                for (dx in 0..2) {
                    for (dz in 0..2) {
                        setLeavesIfAir(chunkData, px + dx, 11, pz + dz, Material.AZALEA_LEAVES)
                    }
                }
            }

            // 高砂岩柱
            else -> {
                for (y in 1..5) fillPillarLayer(chunkData, px, pz, y, Material.SANDSTONE)
                for (y in 6..10) fillPillarLayer(chunkData, px, pz, y, Material.CUT_SANDSTONE)
                for (y in 11..15) fillPillarLayer(chunkData, px, pz, y, Material.SANDSTONE_WALL)
                buildSpire(chunkData, px, pz, 16)
                buildSpireTip(chunkData, px, pz, 18)
            }
        }
    }

    private fun cutSandstoneMix(): Material =
        if (hash(chunkX + chunkZ, 0, 0, SALT_PILLAR) % 2 == 0) Material.CUT_SANDSTONE else Material.CHISELED_SANDSTONE

    /** 把 3×3 的一层填满。 */
    private fun fillPillarLayer(chunkData: ChunkData, px: Int, pz: Int, y: Int, material: Material) {
        for (dx in 0..2) {
            for (dz in 0..2) {
                setWall(chunkData, px + dx, y, pz + dz, material)
            }
        }
    }

    /**
     * 3×3 的塔尖：四条边的中间那格放**朝中心**的楼梯（高的那面朝里，看着就是往里收），
     * 四个角放台阶。
     */
    private fun buildSpire(chunkData: ChunkData, px: Int, pz: Int, y: Int) {
        for (dx in 0..2) {
            for (dz in 0..2) {
                val edge = (dx == 1) != (dz == 1)
                val x = px + dx
                val z = pz + dz
                if (edge) {
                    // 朝中心：北边那格朝南、南边朝北、西边朝东、东边朝西
                    val facing = when {
                        dz == 0 -> BlockFace.SOUTH
                        dz == 2 -> BlockFace.NORTH
                        dx == 0 -> BlockFace.EAST
                        else -> BlockFace.WEST
                    }
                    setStairs(chunkData, x, y, z, Material.SANDSTONE_STAIRS, facing)
                } else {
                    setSlab(chunkData, x, y, z, Material.SANDSTONE_SLAB)
                }
            }
        }
    }

    /** 塔尖顶上再收一格台阶。 */
    private fun buildSpireTip(chunkData: ChunkData, px: Int, pz: Int, y: Int) {
        setSlab(chunkData, px + 1, y, pz + 1, Material.SMOOTH_SANDSTONE_SLAB)
    }

    // ==================== 天窗 / 封顶 ====================

    /** y20：浅绿玻璃 / 深绿玻璃 / 空气随机；y21：屏障。 */
    private fun generateCeiling(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                when (hash(x, 200, z, SALT_CEILING) % 100) {
                    in 0..34 -> chunkData.setBlock(x, glassY, z, Material.LIME_STAINED_GLASS)
                    in 35..69 -> chunkData.setBlock(x, glassY, z, Material.GREEN_STAINED_GLASS)
                    else -> Unit    // 剩下的是空气（洞）
                }
                chunkData.setBlock(x, roofY, z, Material.BARRIER)
            }
        }
    }

    // ==================== 踢脚线 / 藤蔓 / 树叶 ====================

    /**
     * 贴着墙或柱子的一格：
     * - y1 放 [Material.SMOOTH_SANDSTONE_STAIRS]，完整的那一面朝墙（= 踢脚线）；
     * - 区域墙内侧那一格从 y2 往上长藤蔓 / 橡树树叶；
     * - 墙与柱子周围再零星挂一些树叶，模拟粗藤蔓。
     */
    private fun generateWallDecor(chunkData: ChunkData) {
        // 第一遍：先决定每格踢脚线的朝向（shape 要看邻居，得先全都算出来）
        for (x in 0..15) {
            for (z in 0..15) {
                if (wallCells[x][z]) continue
                if (doorFront[x][z]) continue
                if (opening[x][z]) continue          // 门洞本身（墙被挖掉那几格）不放踢脚线
                if (chunkData.getType(x, wallBottomY, z) != Material.AIR) continue
                baseboardFacing[x][z] = wallDirectionAt(x, z) ?: continue
            }
        }

        // 第二遍：按邻居决定 shape 再放楼梯
        for (x in 0..15) {
            for (z in 0..15) {
                val facing = baseboardFacing[x][z] ?: continue
                setStairs(
                    chunkData, x, wallBottomY, z, Material.SMOOTH_SANDSTONE_STAIRS,
                    facing, baseboardShapeAt(x, z, facing)
                )
            }
        }

        // 区域墙内侧：y2 往上藤蔓 / 树叶
        for (side in 0..3) {
            if (plans[side] == null) continue
            for (i in 0..15) {
                // 内侧那一格（cx,cz）与它贴着的墙上那一格（wx,wz）
                val cx: Int
                val cz: Int
                val wx: Int
                val wz: Int
                when (side) {
                    0 -> { cx = i; cz = 1; wx = i; wz = 0 }
                    1 -> { cx = i; cz = 14; wx = i; wz = 15 }
                    2 -> { cx = 1; cz = i; wx = 0; wz = i }
                    else -> { cx = 14; cz = i; wx = 15; wz = i }
                }
                if (wallCells[cx][cz]) continue
                for (y in (wallBottomY + 1)..wallTopY) {
                    // 这一层墙被门洞挖掉了 → 不要挂出没墙可依的藤蔓
                    if (chunkData.getType(wx, y, wz) == Material.AIR) continue
                    // 踢脚线正上方留空：楼梯顶上压着藤蔓/树叶看着就是「状态不对」
                    if (y == wallBottomY + 1 && baseboardFacing[cx][cz] != null) continue
                    when (hash(cx, y, cz, SALT_VINE) % 100) {
                        in 0..33 -> putVine(chunkData, cx, y, cz, wallFaceOf(side))
                        in 34..54 -> setLeavesIfAir(chunkData, cx, y, cz, Material.OAK_LEAVES)
                        else -> Unit
                    }
                }
            }
        }

        // 墙 / 柱子周围挂树叶
        for (x in 0..15) {
            for (z in 0..15) {
                if (!wallCells[x][z]) continue
                for (dir in 0..3) {
                    val nx = x + DX[dir]
                    val nz = z + DZ[dir]
                    if (nx !in 0..15 || nz !in 0..15) continue
                    if (wallCells[nx][nz] || opening[nx][nz]) continue
                    val h = hash(nx, 300 + dir, nz, SALT_LEAF) % 100
                    if (h >= 18) continue
                    val y = 2 + hash(nx, 301 + dir, nz, SALT_LEAF) % 17      // 2..18
                    // 同上：踢脚线正上方不放
                    if (y == wallBottomY + 1 && baseboardFacing[nx][nz] != null) continue
                    setLeavesIfAir(chunkData, nx, y, nz, Material.OAK_LEAVES)
                }
            }
        }
    }

    /**
     * 这一格的踢脚线该朝哪边。
     *
     * 先看四正方向（普通那条贴着墙的踢脚线）；都没有时再看**四个角**——
     * 柱子 / 墙的拐角是斜着的，光看四正方向会在角上缺一格，所以这里补上，
     * 朝「贴到柱子的那个方向」的 x 轴那一面（楼梯的完整面正好顶住柱子的一个侧面）。
     */
    private fun wallDirectionAt(x: Int, z: Int): BlockFace? {
        for (dir in 0..3) {
            if (wallAt(x + DX[dir], z + DZ[dir])) return FACE_OF[dir]
        }
        for (dir in 0..3) {
            if (wallAt(x + DIAG_DX[dir], z + DIAG_DZ[dir])) return DIAG_FACE[dir]
        }
        return null
    }

    /**
     * 这一格踢脚线的 `shape`。
     *
     * 楼梯的 `shape` 只设 `facing` 的话默认是 `STRAIGHT`，拐角处就接不上（一边高一边缺角）。
     * 规则（和原版的判定一致，只是我们这边「墙」是斜着的，所以多一条）：
     * 1. 左右哪一侧是墙 → 内角补在那一侧（`INNER_LEFT` / `INNER_RIGHT`）；
     * 2. 否则左右哪一侧接着另一条踢脚线（柱子 / 墙的斜角）→ 补在那一侧；
     * 3. 两侧一样（直墙那条、或者两侧都是墙）→ `STRAIGHT`。
     */
    private fun baseboardShapeAt(x: Int, z: Int, facing: BlockFace): Stairs.Shape {
        val left = leftOf(facing)
        val right = rightOf(facing)
        val lx = x + left.modX
        val lz = z + left.modZ
        val rx = x + right.modX
        val rz = z + right.modZ

        val leftWall = wallAt(lx, lz)
        val rightWall = wallAt(rx, rz)
        val leftBoard = baseboardAt(lx, lz)
        val rightBoard = baseboardAt(rx, rz)

        // 正前方不是墙 → 这是柱子 / 墙的斜角，必须补一角，否则看着就是缺了一块
        if (!wallAt(x + facing.modX, z + facing.modZ)) {
            return when {
                leftWall -> Stairs.Shape.INNER_LEFT
                rightWall -> Stairs.Shape.INNER_RIGHT
                leftBoard -> Stairs.Shape.INNER_LEFT
                rightBoard -> Stairs.Shape.INNER_RIGHT
                else -> Stairs.Shape.INNER_RIGHT
            }
        }

        // 正前方是墙：只有左右「只有一侧」有东西（拐角）才补，直墙那条两侧都一样 → STRAIGHT
        if (leftWall != rightWall) {
            return if (leftWall) Stairs.Shape.INNER_LEFT else Stairs.Shape.INNER_RIGHT
        }
        if (leftBoard != rightBoard) {
            return if (leftBoard) Stairs.Shape.INNER_LEFT else Stairs.Shape.INNER_RIGHT
        }
        return Stairs.Shape.STRAIGHT
    }

    private fun wallAt(x: Int, z: Int): Boolean {
        if (x in 0..15 && z in 0..15) return wallCells[x][z]
        // 区块外面有两类墙会跨过来：跨区块墙线、邻区块的区域边界墙。
        // 看不到它们的话，墙在区块边上的自由端就收不了口（踢脚线会平着断掉，不拐弯）。
        val wx = chunkX * 16 + x
        val wz = chunkZ * 16 + z
        return lineWallAt(wx, wz) || neighbourRegionWall(wx, wz)
    }

    /**
     * 区块外那一格是不是**邻区块的区域边界墙**（厚 1，正好贴着交界）。
     * 邻区块那一侧得是区域边界、那一格落在它的墙带里、而且不是门洞。
     */
    private fun neighbourRegionWall(wx: Int, wz: Int): Boolean {
        val ncx = Math.floorDiv(wx, 16)
        val ncz = Math.floorDiv(wz, 16)
        val lx = Math.floorMod(wx, 16)
        val lz = Math.floorMod(wz, 16)

        val side = when {
            lz == 0 -> RegionBoundary.NORTH
            lz == 15 -> RegionBoundary.SOUTH
            lx == 0 -> RegionBoundary.WEST
            lx == 15 -> RegionBoundary.EAST
            else -> return false
        }
        val plan = RegionGateway.planFor(ncx, ncz, worldSeed, side) ?: return false
        if (plan.passage == RegionGateway.Passage.NONE) return true
        // 有门 / 洞口：开孔那几格不是墙
        val i = if (side == RegionBoundary.NORTH || side == RegionBoundary.SOUTH) lx else lz
        val openHere = i in plan.gapStart until plan.gapEnd &&
                wallBottomY >= RegionGateway.PASSAGE_BOTTOM &&
                wallBottomY < RegionGateway.PASSAGE_BOTTOM + plan.height
        return !openHere
    }

    /**
     * 世界坐标 (wx,wz) 是不是「跨区块墙线」上的墙。
     *
     * 纯计算，区块内外都能算 —— [buildWallLine] 用的是同一套判定（世界坐标 x ≡ 8 (mod 32)、
     * 每条 32 格一抽），所以跨区块的衔接必然一致。
     */
    private fun lineWallAt(wx: Int, wz: Int): Boolean {
        val gx = Math.floorDiv(wx, 32)
        val gz = Math.floorDiv(wz, 32)
        if (Math.floorMod(wx, 32) == 8 &&
            rawHash(gx, gz, 0, SALT_LINE_V) % 100 < LINE_CHANCE
        ) return true
        if (Math.floorMod(wz, 32) == 8 &&
            rawHash(gx, gz, 1, SALT_LINE_H) % 100 < LINE_CHANCE
        ) return true
        return false
    }

    private fun baseboardAt(x: Int, z: Int): Boolean =
        x in 0..15 && z in 0..15 && baseboardFacing[x][z] != null

    /** 朝 [f] 看时的左手边（北→西）。 */
    private fun leftOf(f: BlockFace): BlockFace = when (f) {
        BlockFace.NORTH -> BlockFace.WEST
        BlockFace.WEST -> BlockFace.SOUTH
        BlockFace.SOUTH -> BlockFace.EAST
        else -> BlockFace.NORTH
    }

    /** 朝 [f] 看时的右手边（北→东）。 */
    private fun rightOf(f: BlockFace): BlockFace = when (f) {
        BlockFace.NORTH -> BlockFace.EAST
        BlockFace.EAST -> BlockFace.SOUTH
        BlockFace.SOUTH -> BlockFace.WEST
        else -> BlockFace.NORTH
    }
    private fun wallFaceOf(side: Int): BlockFace = when (side) {
        0 -> BlockFace.NORTH
        1 -> BlockFace.SOUTH
        2 -> BlockFace.WEST
        else -> BlockFace.EAST
    }

    // ==================== 地面装饰 ====================

    /** 草丛 / 高草丛 / 蕨 / 杜鹃花丛，以及偶尔一棵杜鹃树。 */
    private fun generateGround(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                if (wallCells[x][z]) continue
                if (opening[x][z]) continue              // 门洞里不长草
                if (chunkData.getType(x, groundY, z) != Material.GRASS_BLOCK) continue
                if (chunkData.getType(x, wallBottomY, z) != Material.AIR) continue

                when (hash(x, 400, z, SALT_PLANT) % 100) {
                    in 0..45 -> chunkData.setBlock(x, 1, z, Material.SHORT_GRASS)     // 草 46%
                    in 46..85 -> {
                        // 高草丛是两格高：上半截要用 half = TOP 单独放，不然只有底部
                        if (!chunkData.setDoublePlant(x, 1, z, Material.TALL_GRASS)) {
                            chunkData.setBlock(x, 1, z, Material.SHORT_GRASS)
                        }
                    }                                                                 // 高草丛 40%
                    in 86..90 -> chunkData.setBlock(x, 1, z, Material.FERN)           // 蕨 5%
                    in 91..94 -> chunkData.setBlock(
                        x, 1, z,
                        if (hash(x, 401, z, SALT_PLANT) % 2 == 0) Material.AZALEA else Material.FLOWERING_AZALEA
                    )                                                                 // 杜鹃花丛 4%
                    else -> Unit                                                      // 只剩 5% 是空的
                }
            }
        }

        // 杜鹃树：一格里最多一棵，整棵必须能放进区块内（树冠半径 2）
        if (hash(chunkX, 500, chunkZ, SALT_TREE) % 100 >= TREE_CHANCE) return
        val tx = 4 + rawHash(chunkX, 501, chunkZ, SALT_TREE) % 8      // 4..11
        val tz = 4 + rawHash(chunkX, 502, chunkZ, SALT_TREE) % 8
        val trunk = 4 + rawHash(chunkX, 503, chunkZ, SALT_TREE) % 3   // 4..6

        // 树干位置必须空着
        for (y in 1..(trunk + 3)) {
            if (chunkData.getType(tx, y, tz) != Material.AIR) return
        }

        for (y in 1..trunk) chunkData.setBlock(tx, y, tz, Material.OAK_LOG)

        val leaf = if (hash(tx, 504, tz, SALT_TREE) % 2 == 0) Material.AZALEA_LEAVES else Material.FLOWERING_AZALEA_LEAVES
        for (dy in 0..2) {
            val y = trunk - 1 + dy
            val r = if (dy == 2) 1 else 2
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (dx == 0 && dz == 0 && dy <= 1) continue
                    if (kotlin.math.abs(dx) == r && kotlin.math.abs(dz) == r && r == 2) continue
                    val x = tx + dx
                    val z = tz + dz
                    if (x !in 0..15 || z !in 0..15) continue
                    if (chunkData.getType(x, y, z) == Material.OAK_LOG) continue
                    // 只填空气：树冠挨着柱子 / 墙时不要把方块盖掉
                    setLeavesIfAir(chunkData, x, y, z, leaf)
                }
            }
        }
        setLeavesIfAir(chunkData, tx, trunk + 1, tz, leaf)
    }

    // ==================== 小工具 ====================

    private fun setWall(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material) {
        chunkData.setBlock(x, y, z, material)
        // 只在 y1 记「这一格是墙」：门洞只挖了 y1~y3，门上面的墙照样存在，
        // 如果按整列记，踢脚线就会铺到门洞里去了。
        if (y == wallBottomY) wallCells[x][z] = true
    }

    /** 只在是空气时放，用于树叶这类装饰。 */
    private fun setIfAir(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material): Boolean {
        if (x !in 0..15 || z !in 0..15 || y !in 0..255) return false
        if (chunkData.getType(x, y, z) != Material.AIR) return false
        chunkData.setBlock(x, y, z, material)
        return true
    }

    /**
     * 树叶：**persistent = true（永不凋零）**。
     *
     * 不放这个标记的话，周围没有原木的树叶会在区块 tick 之后自己掉光，
     * 花园里挂的那些「粗藤蔓」树叶就会一块块消失。
     */
    private fun leavesData(material: Material): Leaves {
        val data = material.createBlockData() as Leaves
        data.isPersistent = true
        return data
    }

    private fun setLeavesIfAir(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material): Boolean {
        if (x !in 0..15 || z !in 0..15 || y !in 0..255) return false
        if (chunkData.getType(x, y, z) != Material.AIR) return false
        chunkData.setBlock(x, y, z, leavesData(material))
        return true
    }

    private fun setStairs(
        chunkData: ChunkData,
        x: Int,
        y: Int,
        z: Int,
        material: Material,
        facing: BlockFace,
        shape: Stairs.Shape = Stairs.Shape.STRAIGHT
    ) {
        val data = material.createBlockData() as Stairs
        data.facing = facing          // 楼梯「高的那一面」朝 facing，所以踢脚线的完整面正好贴着墙
        data.shape = shape            // 拐角一定要给 shape，默认的 STRAIGHT 在角上接不上
        data.half = Bisected.Half.BOTTOM
        chunkData.setBlock(x, y, z, data)
    }

    private fun setSlab(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material) {
        val data = material.createBlockData() as Slab
        data.type = Slab.Type.BOTTOM
        chunkData.setBlock(x, y, z, data)
    }

    /** 藤蔓只填空气，并挂住墙的那一面。 */
    private fun putVine(chunkData: ChunkData, x: Int, y: Int, z: Int, attachTo: BlockFace) {
        if (chunkData.getType(x, y, z) != Material.AIR) return
        val data = Material.VINE.createBlockData() as MultipleFacing
        data.setFace(attachTo, true)
        chunkData.setBlock(x, y, z, data)
    }

    private fun fillLayerMixed(
        chunkData: ChunkData,
        y: Int,
        materials: List<Material>,
        weights: List<Int>
    ) {
        val total = weights.sum()
        for (x in 0..15) {
            for (z in 0..15) {
                var r = hash(x, y, z, SALT_MIX) % total
                var chosen = materials.last()
                for (i in materials.indices) {
                    r -= weights[i]
                    if (r < 0) {
                        chosen = materials[i]
                        break
                    }
                }
                chunkData.setBlock(x, y, z, chosen)
            }
        }
    }

    /**
     * 按区块内局部坐标哈希（内部换算成世界坐标，所以同一个方块在哪个区块里算都一样）。
     * [x] / [z] 必须传 0..15 的局部坐标。
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

    /** 只按传入的坐标哈希（格级决策：坐标本身已经是全局量，不能再叠加区块坐标）。 */
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
        private const val SALT_MIX = 6201L
        private const val SALT_WALL = 6202L
        private const val SALT_CELL = 6203L
        private const val SALT_LINE_V = 6204L
        private const val SALT_LINE_H = 6205L
        private const val SALT_PILLAR = 6206L
        private const val SALT_CEILING = 6207L
        private const val SALT_VINE = 6208L
        private const val SALT_LEAF = 6209L
        private const val SALT_PLANT = 6210L
        private const val SALT_TREE = 6211L

        /** 一条墙线（2 个区块一段）出现的概率（%）。 */
        private const val LINE_CHANCE = 40

        /** 每个区块长一棵杜鹃树的概率（%）。 */
        private const val TREE_CHANCE = 30

        /** 柱子配方数量。 */
        private const val PILLAR_TYPES = 5

        private val DX = intArrayOf(0, 0, -1, 1)
        private val DZ = intArrayOf(-1, 1, 0, 0)
        private val FACE_OF = arrayOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST)

        /** 四个斜角（给柱子 / 墙的拐角补踢脚线用）。 */
        private val DIAG_DX = intArrayOf(1, 1, -1, -1)
        private val DIAG_DZ = intArrayOf(1, -1, 1, -1)
        private val DIAG_FACE = arrayOf(BlockFace.EAST, BlockFace.EAST, BlockFace.WEST, BlockFace.WEST)

        private val bottomMix = listOf(
            Material.SANDSTONE,
            Material.SMOOTH_SANDSTONE,
            Material.BIRCH_PLANKS,
            Material.GREEN_CONCRETE,
            Material.MOSS_BLOCK,
            Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE
        )

        private val stoneMix = listOf(
            Material.DIORITE,
            Material.GRANITE,
            Material.ANDESITE,
            Material.STONE,
            Material.COBBLESTONE
        )
        private val stoneWeights = listOf(1, 1, 1, 1, 1)

        private val deepslateMix = listOf(
            Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE,
            Material.DEEPSLATE_BRICKS,
            Material.DEEPSLATE_TILES,
            Material.CRACKED_DEEPSLATE_BRICKS
        )
        private val deepslateWeights = listOf(5, 3, 2, 2, 1)
    }
}
