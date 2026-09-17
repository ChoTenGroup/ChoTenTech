package io.github.zzzyyylllty.chotentech.worldgeneration.l1.corridors

import io.github.zzzyyylllty.chotentech.worldgeneration.ChunkGen
import io.github.zzzyyylllty.chotentech.worldgeneration.WORLD_FLOOR_MIX_Y
import io.github.zzzyyylllty.chotentech.worldgeneration.fillLayer
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldFloor
import io.github.zzzyyylllty.chotentech.worldgeneration.fillWorldRoof
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionBoundary
import io.github.zzzyyylllty.chotentech.worldgeneration.l1.RegionGateway
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Lantern
import org.bukkit.block.data.type.Stairs
import org.bukkit.generator.ChunkGenerator.ChunkData

/**
 * GL1PlainFabled - 传说段（木质大厅）
 *
 * 一整片 5 格高的木结构大厅：
 *
 * ```
 * y = 6        木板天花板（中间嵌海晶灯 / 荧石 / 蛙明灯）
 * y = 5        灯管（末地烛一排排挂在天花板下）+ 木梁
 * y = 1 ~ 5    内部空间（高 5）：木柱、隔墙 + 门、夹层平台 + 木栅栏、大量家具
 * y = 0        青色陶瓦地板（掺一点青色带釉陶瓦）
 * y = -1 ~ -2  混合石块层
 * y = -11 ~ -3 深板岩混合层（底下的装饰层和基岩见「通用规则」）
 * ```
 *
 * ## 结构都是**世界坐标对齐**的（跨区块必然一致）
 * | 网格 | 内容 |
 * | --- | --- |
 * | 每 6 格 | 一根 1×1 木柱（去皮原木，y1~y5） |
 * | 每 12 格 | 一道木板隔墙（y1~y5，墙上开一个 1 格宽的门洞 + **两格高的门**） |
 * | 12×12 的「房间」 | 40% 概率在里面搭一层 y3 的夹层平台（木板 + 木栅栏栏杆 + 两级台阶上去） |
 *
 * ## 门
 * 门一律用 [Door] BlockData 放**上下两格**（`half = BOTTOM` / `TOP`），
 * 朝向按门洞所在的墙定（贴在墙上），合页由哈希定 ——
 * 以前是直接 `setBlock(Material.OAK_DOOR)`，两格都是「下半部分」，看着就是断的。
 *
 * ## 家具
 * 箱子、木桶、工作台、熔炉、烟熏炉、高炉、书架、讲台、炼药锅、堆肥桶、花盆、织布机、
 * 制图台、锻造台、切石机、铁砧、营火、唱片机、音符盒、床（两格）、椅子（楼梯）、
 * 桌子（木栅栏 + 压力板）、木栅栏与栅栏门、木桶塔、末地烛灯管……
 *
 * 所有随机都是**按位置哈希**（[hash] / [rawHash]）取的，同一区块每次生成都一样。
 */
class GL1PlainFabled(
    private val chunkX: Int = 0,
    private val chunkZ: Int = 0,
    private val worldSeed: Long = 0
) : ChunkGen {

    // ==================== 尺寸 ====================

    /** 地板层（青色陶瓦）。 */
    private val floorY = 0

    /** 内部空间：y1 ~ y5（高 5）。 */
    private val wallBottomY = 1
    private val wallTopY = 5

    /** 天花板层（木板 + 灯）。 */
    private val ceilingY = 6

    /** 夹层平台的高度。 */
    private val mezzanineY = 3

    /** 区域交界门洞正前方那几格（不放结构 / 家具，免得把门堵死）。 */
    private val doorFront = Array(16) { BooleanArray(16) }

    private fun openArea(): IntRange = wallBottomY..wallTopY

    // ==================== 主流程 ====================

    override fun generate(chunkData: ChunkData) {
        generateFoundation(chunkData)

        val plans = generateRegionWalls(chunkData)
        markDoorFront(plans)

        generateStructure(chunkData)      // 木柱、隔墙、门、夹层、栏杆
        generateCeiling(chunkData)        // y6 木板天花板 + 嵌灯
        generateLightTubes(chunkData)     // 灯管
        generateFurniture(chunkData)      // 家具

        RegionGateway.placeDoors(chunkData, plans[0])
        RegionGateway.placeDoors(chunkData, plans[1])
        RegionGateway.placeDoors(chunkData, plans[2])
        RegionGateway.placeDoors(chunkData, plans[3])
    }

    // ==================== 地层 ====================

    private fun generateFoundation(chunkData: ChunkData) {
        chunkData.fillWorldFloor()
        chunkData.fillWorldRoof()

        for (y in WORLD_FLOOR_MIX_Y..-1) fillLayerStoneMixed(chunkData, y)

        // 地板：青色陶瓦（掺一成青色带釉陶瓦，看着更像铺过的地）
        for (x in 0..15) {
            for (z in 0..15) {
                val material = if (hash(x, 0, z, SALT_FLOOR) % 100 < 12) {
                    Material.CYAN_GLAZED_TERRACOTTA
                } else {
                    Material.CYAN_TERRACOTTA
                }
                chunkData.setBlock(x, floorY, z, material)
            }
        }

        // 天花板上方填实（结构本身只到 y6）
        for (y in (ceilingY + 1)..15) chunkData.fillLayer(y, Material.POLISHED_TUFF)
    }

    // ==================== 区域边界墙 ====================

    /**
     * 只有 `planFor` 不为 null 的那一侧才砌墙（邻居是别的区域）。
     * 传说段内部区块之间是通的，房间靠 [generateStructure] 的隔墙分隔。
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
                    chunkData.setBlock(i, y, 0, planksAt(chunkX * 16 + i, y, chunkZ * 16, SALT_REGION))
                }
                if (plans[1] != null && !RegionGateway.opensAt(plans[1], i, y)) {
                    chunkData.setBlock(i, y, 15, planksAt(chunkX * 16 + i, y, chunkZ * 16 + 15, SALT_REGION))
                }
            }
        }
        for (i in 0..15) {
            for (y in wallBottomY..wallTopY) {
                if (plans[2] != null && !RegionGateway.opensAt(plans[2], i, y)) {
                    chunkData.setBlock(0, y, i, planksAt(chunkX * 16, y, chunkZ * 16 + i, SALT_REGION))
                }
                if (plans[3] != null && !RegionGateway.opensAt(plans[3], i, y)) {
                    chunkData.setBlock(15, y, i, planksAt(chunkX * 16 + 15, y, chunkZ * 16 + i, SALT_REGION))
                }
            }
        }
        return plans
    }

    private fun markDoorFront(plans: Array<RegionGateway.Plan?>) {
        for (side in 0..3) {
            val plan = plans[side] ?: continue
            for (i in plan.gapStart until plan.gapEnd) {
                if (i !in 0..15) continue
                when (side) {
                    0 -> doorFront[i][1] = true
                    1 -> doorFront[i][14] = true
                    2 -> doorFront[1][i] = true
                    else -> doorFront[14][i] = true
                }
            }
        }
    }

    // ==================== 木结构 ====================

    /** 木柱（每 6 格）、隔墙 + 门（每 12 格）、夹层平台 + 栏杆。 */
    private fun generateStructure(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                if (doorFront[x][z]) continue
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z

                // 柱子：世界坐标 6 的倍数
                if (Math.floorMod(wx, 6) == 0 && Math.floorMod(wz, 6) == 0) {
                    val log = LOGS[rawHash(wx, 0, wz, SALT_PILLAR) % LOGS.size]
                    for (y in wallBottomY..wallTopY) setBody(chunkData, x, y, z, log)
                    continue
                }

                // 隔墙：世界坐标 ≡ 6 (mod 12) 的那两条线
                val onXWall = Math.floorMod(wx, 12) == 6 && !isDoorGap(wx, wz, vertical = true)
                val onZWall = Math.floorMod(wz, 12) == 6 && !isDoorGap(wx, wz, vertical = false)
                if (onXWall || onZWall) {
                    for (y in wallBottomY..wallTopY) {
                        setBody(chunkData, x, y, z, planksAt(wx, y, wz, SALT_WALL))
                    }
                    continue
                }

                // 门洞里那个「门」本身（两格高 + 朝向 + 合页）
                if (isDoorCell(wx, wz)) placeStructureDoor(chunkData, x, z, wx, wz)
            }
        }

        generateMezzanine(chunkData)
    }

    /**
     * 隔墙上的门洞：世界坐标 (wx,wz) 是不是门洞那一格。
     * 门洞位置由墙段（12 格一段）的哈希定，所以跨区块两侧算出来一致。
     */
    private fun isDoorGap(wx: Int, wz: Int, vertical: Boolean): Boolean {
        // vertical = true 表示这是一道沿 Z 走的墙（x ≡ 6 mod 12），门洞开在 z 方向
        val segAlong = if (vertical) Math.floorDiv(wz, 12) else Math.floorDiv(wx, 12)
        val segCross = if (vertical) Math.floorDiv(wx, 12) else Math.floorDiv(wz, 12)
        val spot = 4 + rawHash(segCross, segAlong, if (vertical) 0 else 1, SALT_DOOR) % 5   // 4..8
        val along = if (vertical) Math.floorMod(wz, 12) else Math.floorMod(wx, 12)
        return along == spot
    }

    private fun isDoorCell(wx: Int, wz: Int): Boolean {
        if (Math.floorMod(wx, 12) == 6 && Math.floorMod(wz, 12) != 6) {
            return isDoorGap(wx, wz, vertical = true)
        }
        if (Math.floorMod(wz, 12) == 6 && Math.floorMod(wx, 12) != 6) {
            return isDoorGap(wx, wz, vertical = false)
        }
        return false
    }

    /** 隔墙门洞里的门：上下两格 + 朝向 + 合页。 */
    private fun placeStructureDoor(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        // 沿 Z 的墙 → 门朝东/西；沿 X 的墙 → 门朝南/北
        val facing = if (Math.floorMod(wx, 12) == 6) BlockFace.EAST else BlockFace.SOUTH
        val hinge = if (rawHash(wx, 0, wz, SALT_DOOR) % 2 == 0) Door.Hinge.LEFT else Door.Hinge.RIGHT
        val material = if (rawHash(wx, 1, wz, SALT_DOOR) % 4 == 0) Material.DARK_OAK_DOOR else Material.OAK_DOOR
        setDoor(chunkData, x, wallBottomY, z, facing, hinge, material)
    }

    /** 夹层平台：12×12 房间里 40% 概率来一层，带木栅栏栏杆和两级台阶。 */
    private fun generateMezzanine(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                if (doorFront[x][z]) continue
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val cellX = Math.floorDiv(wx, 12)
                val cellZ = Math.floorDiv(wz, 12)
                if (rawHash(cellX, 7, cellZ, SALT_MEZZ) % 100 >= 40) continue

                val lx = Math.floorMod(wx, 12)   // 0..11
                val lz = Math.floorMod(wz, 12)
                // 平台铺在离隔墙留出 1 格的范围里
                val inside = lx in 2..9 && lz in 2..9
                if (!inside) continue

                val onEdge = lx == 2 || lx == 9 || lz == 2 || lz == 9
                if (onEdge) {
                    // 边缘：木栅栏栏杆（每隔一格留个缺口方便上下）
                    if (Math.floorMod(wx + wz, 3) != 0) {
                        setIfAir(chunkData, x, mezzanineY, z, FENCES[rawHash(wx, 2, wz, SALT_MEZZ) % FENCES.size])
                    }
                    // 边缘下面来一根托梁（y2），夹层看着就不是浮空的
                    setIfAir(chunkData, x, mezzanineY - 1, z, planksAt(wx, mezzanineY - 1, wz, SALT_MEZZ))
                } else {
                    setIfAir(chunkData, x, mezzanineY, z, planksAt(wx, mezzanineY, wz, SALT_MEZZ))
                }

                // 上去的两级台阶（房间正中那一列）
                val mid = 5 + rawHash(cellX, 8, cellZ, SALT_MEZZ) % 2      // 5..6
                if (lx == mid && lz == 1) setStairs(chunkData, x, wallBottomY, z, Material.OAK_STAIRS, BlockFace.SOUTH)
                if (lx == mid && lz == 2) setStairs(chunkData, x, wallBottomY + 1, z, Material.OAK_STAIRS, BlockFace.SOUTH)
            }
        }
    }

    // ==================== 天花板 / 灯管 ====================

    private fun generateCeiling(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val h = hash(x, 99, z, SALT_CEILING) % 100
                val material = when {
                    h < 5 -> Material.SEA_LANTERN
                    h < 8 -> Material.GLOWSTONE
                    h < 10 -> Material.OCHRE_FROGLIGHT
                    h < 12 -> Material.SHROOMLIGHT
                    else -> planksAt(wx, ceilingY, wz, SALT_CEILING)
                }
                chunkData.setBlock(x, ceilingY, z, material)
            }
        }
    }

    /** 灯管：天花板下每 12 格一排末地烛（细长像灯管），再点缀几个吊灯。 */
    private fun generateLightTubes(chunkData: ChunkData) {
        for (x in 0..15) {
            for (z in 0..15) {
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                val tube = Math.floorMod(wx, 12) == 11 || Math.floorMod(wz, 12) == 11
                if (tube) {
                    setEndRodDown(chunkData, x, wallTopY, z)
                } else if (hash(x, 77, z, SALT_LIGHT) % 100 < 4) {
                    setLantern(chunkData, x, wallTopY, z)
                }
            }
        }
    }

    // ==================== 家具 ====================

    /** 家具：按世界坐标的粗网格撒，只在空的、能站人的地方放（不会悬空）。 */
    private fun generateFurniture(chunkData: ChunkData) {
        val cell = 4
        for (x in 0..15) {
            for (z in 0..15) {
                if (doorFront[x][z]) continue
                val wx = chunkX * 16 + x
                val wz = chunkZ * 16 + z
                if (Math.floorMod(wx, cell) != 0 || Math.floorMod(wz, cell) != 0) continue
                if (hash(x, 30, z, SALT_FURNITURE) % 100 >= 55) continue

                when (hash(x, 31, z, SALT_FURNITURE) % 12) {
                    0 -> placeTable(chunkData, x, z, wx, wz)
                    1 -> placeChairs(chunkData, x, z, wx, wz)
                    2 -> placeBedGroup(chunkData, x, z, wx, wz)
                    3 -> placeShelves(chunkData, x, z, wx, wz)
                    4 -> placeWorkstation(chunkData, x, z, wx, wz)
                    5 -> placeCrates(chunkData, x, z, wx, wz)
                    6 -> placeChestCorner(chunkData, x, z, wx, wz)
                    7 -> placeKitchen(chunkData, x, z, wx, wz)
                    8 -> placeCampfire(chunkData, x, z, wx, wz)
                    9 -> placeFenceCorner(chunkData, x, z, wx, wz)
                    10 -> placePottedPlants(chunkData, x, z, wx, wz)
                    else -> placeJukeboxCorner(chunkData, x, z, wx, wz)
                }
            }
        }
    }

    /** 桌子：木栅栏腿 + 压力板桌面（四条腿在四角）。 */
    private fun placeTable(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val fence = FENCES[rawHash(wx, 3, wz, SALT_FURNITURE) % FENCES.size]
        val plate = PLATES[rawHash(wx, 4, wz, SALT_FURNITURE) % PLATES.size]
        var any = false
        for (dx in 0..1) {
            for (dz in 0..1) {
                any = setIfAir(chunkData, x + dx, wallBottomY, z + dz, fence) || any
            }
        }
        if (!any) return
        for (dx in 0..1) {
            for (dz in 0..1) {
                setIfAir(chunkData, x + dx, wallBottomY + 1, z + dz, plate)
            }
        }
    }

    /** 椅子：两把楼梯椅 + 中间木栅栏小桌。 */
    private fun placeChairs(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val stair = STAIR_WOODS[rawHash(wx, 5, wz, SALT_FURNITURE) % STAIR_WOODS.size]
        val chair = when (rawHash(wx, 6, wz, SALT_FURNITURE) % 4) {
            0 -> Material.OAK_STAIRS
            1 -> Material.SPRUCE_STAIRS
            2 -> Material.BIRCH_STAIRS
            else -> Material.DARK_OAK_STAIRS
        }
        setStairs(chunkData, x, wallBottomY, z, chair, BlockFace.EAST)
        setStairs(chunkData, x + 2, wallBottomY, z, chair, BlockFace.WEST)
        // 中间那根木栅栏当小桌，灯挂在它顶上（柱子没放成就不挂，免得悬空）
        if (setIfAir(chunkData, x + 1, wallBottomY, z, stair)) {
            setIfAir(chunkData, x + 1, wallBottomY + 1, z, Material.LANTERN)
        }
    }

    /** 床：两格（FOOT + HEAD），放在角落。 */
    private fun placeBedGroup(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val bed = BEDS[rawHash(wx, 9, wz, SALT_FURNITURE) % BEDS.size]
        val facing = FACES[rawHash(wx, 10, wz, SALT_FURNITURE) % 4]
        // 先确认两格都是空的，避免半张床
        if (!isAirAt(chunkData, x, wallBottomY, z)) return
        if (!isAirAt(chunkData, x + facing.modX, wallBottomY, z + facing.modZ)) return
        setBedPair(chunkData, x, wallBottomY, z, facing, bed)
    }

    /** 书架 + 讲台（书房角落）。 */
    private fun placeShelves(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        for (dx in 0..1) {
            if (!setIfAir(chunkData, x + dx, wallBottomY, z, Material.BOOKSHELF)) continue
            setIfAir(chunkData, x + dx, wallBottomY + 1, z, Material.BOOKSHELF)
        }
        setIfAir(chunkData, x, wallBottomY, z + 1, Material.LECTERN)
        setIfAir(chunkData, x + 1, wallBottomY, z + 1, Material.CHISELED_BOOKSHELF)
    }

    /** 工作台一类：工作台 / 熔炉 / 烟熏炉 / 高炉 / 锻造台 / 切石机 / 制图台 / 织布机。 */
    private fun placeWorkstation(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val pick = rawHash(wx, 11, wz, SALT_FURNITURE) % 8
        val material = when (pick) {
            0 -> Material.CRAFTING_TABLE
            1 -> Material.FURNACE
            2 -> Material.SMOKER
            3 -> Material.BLAST_FURNACE
            4 -> Material.SMITHING_TABLE
            5 -> Material.STONECUTTER
            6 -> Material.CARTOGRAPHY_TABLE
            else -> Material.LOOM
        }
        setIfAir(chunkData, x, wallBottomY, z, material)
        if (setIfAir(chunkData, x + 1, wallBottomY, z, Material.BARREL)) {
            setIfAir(chunkData, x + 1, wallBottomY + 1, z, Material.BARREL)
        }
    }

    /** 木桶塔 / 木箱堆。 */
    private fun placeCrates(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val height = 1 + rawHash(wx, 12, wz, SALT_FURNITURE) % 2      // 1..2
        for (y in 0 until height) {
            if (!setIfAir(chunkData, x, wallBottomY + y, z, Material.BARREL)) break
        }
        setIfAir(chunkData, x + 1, wallBottomY, z, Material.CHEST)
    }

    /** 箱子 + 铁砧角落。 */
    private fun placeChestCorner(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        setIfAir(chunkData, x, wallBottomY, z, Material.CHEST)
        setIfAir(chunkData, x, wallBottomY, z + 1, Material.ANVIL)
        setIfAir(chunkData, x + 1, wallBottomY, z, Material.TRAPPED_CHEST)
    }

    /** 厨房角落：炼药锅 / 堆肥桶 / 烟熏炉 / 木桶。 */
    private fun placeKitchen(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        setIfAir(chunkData, x, wallBottomY, z, Material.CAULDRON)
        setIfAir(chunkData, x, wallBottomY, z + 1, Material.COMPOSTER)
        setIfAir(chunkData, x + 1, wallBottomY, z, Material.SMOKER)
        if (setIfAir(chunkData, x + 1, wallBottomY, z + 1, Material.BARREL)) {
            setIfAir(chunkData, x + 1, wallBottomY + 1, z + 1, Material.BARREL)
        }
    }

    /** 营火（上面一节节叠上去，不会悬空）。 */
    private fun placeCampfire(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        if (!setIfAir(chunkData, x, wallBottomY, z, Material.CAMPFIRE)) return
        if (!setIfAir(chunkData, x, wallBottomY + 1, z, FENCES[rawHash(wx, 13, wz, SALT_FURNITURE) % FENCES.size])) return
        setIfAir(chunkData, x, wallBottomY + 2, z, Material.LANTERN)
    }

    /** 木栅栏围栏 + 栅栏门。 */
    private fun placeFenceCorner(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        val fence = FENCES[rawHash(wx, 14, wz, SALT_FURNITURE) % FENCES.size]
        for (i in 0..2) {
            // 中间留一格放栅栏门
            val material = if (i == 1) Material.OAK_FENCE_GATE else fence
            setIfAir(chunkData, x + i, wallBottomY, z, material)
            if (i != 1) setIfAir(chunkData, x + i, wallBottomY + 1, z, fence)
        }
        // 灯挂在栅栏柱顶上（柱子没放成就不挂，免得悬空）
        if (chunkData.getType(x + 2, wallBottomY + 1, z) != Material.AIR) {
            setIfAir(chunkData, x + 2, wallBottomY + 2, z, Material.LANTERN)
        }
    }

    /** 花盆 / 盆栽。 */
    private fun placePottedPlants(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        setIfAir(chunkData, x, wallBottomY, z, Material.FLOWER_POT)
        setIfAir(chunkData, x + 1, wallBottomY, z, Material.FLOWER_POT)
        setIfAir(chunkData, x, wallBottomY, z + 1, Material.HAY_BLOCK)
        setIfAir(chunkData, x + 1, wallBottomY, z + 1, Material.HAY_BLOCK)
    }

    /** 唱片机 + 音符盒 + 讲台（音乐角）。 */
    private fun placeJukeboxCorner(chunkData: ChunkData, x: Int, z: Int, wx: Int, wz: Int) {
        setIfAir(chunkData, x, wallBottomY, z, Material.JUKEBOX)
        setIfAir(chunkData, x + 1, wallBottomY, z, Material.NOTE_BLOCK)
        if (setIfAir(chunkData, x, wallBottomY, z + 1, Material.NOTE_BLOCK)) {
            // 音符盒上面再挂灯，不容易悬空
            setIfAir(chunkData, x, wallBottomY + 1, z + 1, Material.LANTERN)
        }
    }

    // ==================== 小工具 ====================

    /** 放结构本体（会覆盖空气，但不会覆盖已有方块）。 */
    private fun setBody(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material) {
        if (chunkData.getType(x, y, z) == Material.AIR) chunkData.setBlock(x, y, z, material)
    }

    private fun isAirAt(chunkData: ChunkData, x: Int, y: Int, z: Int): Boolean =
        x in 0..15 && z in 0..15 && chunkData.getType(x, y, z) == Material.AIR

    /** 只在是空气时放。 */
    private fun setIfAir(chunkData: ChunkData, x: Int, y: Int, z: Int, material: Material): Boolean {
        if (x !in 0..15 || z !in 0..15 || y !in 0..255) return false
        if (chunkData.getType(x, y, z) != Material.AIR) return false
        chunkData.setBlock(x, y, z, material)
        return true
    }

    private fun setStairs(
        chunkData: ChunkData,
        x: Int,
        y: Int,
        z: Int,
        material: Material,
        facing: BlockFace
    ) {
        if (!isAirAt(chunkData, x, y, z)) return
        val data = material.createBlockData() as Stairs
        data.facing = facing
        data.half = Bisected.Half.BOTTOM
        chunkData.setBlock(x, y, z, data)
    }

    /** 门：上下两格。以前只放 `Material.OAK_DOOR`，两格都是下半部分，所以是断的。 */
    private fun setDoor(
        chunkData: ChunkData,
        x: Int,
        y: Int,
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
        chunkData.setBlock(x, y, z, lower)

        val upper = material.createBlockData() as Door
        upper.facing = facing
        upper.hinge = hinge
        upper.isOpen = false
        upper.half = Bisected.Half.TOP
        chunkData.setBlock(x, y + 1, z, upper)
    }

    /** 床：脚在 (x,y,z)、头朝 [facing]。 */
    private fun setBedPair(
        chunkData: ChunkData,
        x: Int,
        y: Int,
        z: Int,
        facing: BlockFace,
        material: Material
    ) {
        val foot = material.createBlockData() as Bed
        foot.facing = facing
        foot.part = Bed.Part.FOOT
        chunkData.setBlock(x, y, z, foot)

        val head = material.createBlockData() as Bed
        head.facing = facing
        head.part = Bed.Part.HEAD
        chunkData.setBlock(x + facing.modX, y, z + facing.modZ, head)
    }

    /** 末地烛朝下（贴在天花板下，看着就是一根灯管）。 */
    private fun setEndRodDown(chunkData: ChunkData, x: Int, y: Int, z: Int) {
        if (!isAirAt(chunkData, x, y, z)) return
        val rod = Material.END_ROD.createBlockData()
        val directional = rod as org.bukkit.block.data.Directional
        directional.facing = BlockFace.DOWN
        chunkData.setBlock(x, y, z, rod)
    }

    /** 吊灯。 */
    private fun setLantern(chunkData: ChunkData, x: Int, y: Int, z: Int) {
        if (!isAirAt(chunkData, x, y, z)) return
        val lantern = Material.LANTERN.createBlockData() as Lantern
        lantern.isHanging = true
        chunkData.setBlock(x, y, z, lantern)
    }

    private fun planksAt(wx: Int, wy: Int, wz: Int, salt: Long): Material =
        PLANKS[rawHash(wx, wy, wz, salt) % PLANKS.size]

    private fun fillLayerStoneMixed(chunkData: ChunkData, y: Int) {
        for (x in 0..15) {
            for (z in 0..15) {
                chunkData.setBlock(x, y, z, STONE_MIX[hash(x, y, z, SALT_STONE) % STONE_MIX.size])
            }
        }
    }

    /** 按区块内局部坐标哈希（内部换算成世界坐标，所以同一个方块在哪个区块里算都一样）。 */
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

    /** 只按传入的坐标哈希（坐标本身已经是全局量的场景）。 */
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
        private const val SALT_FLOOR = 7101L
        private const val SALT_STONE = 7102L
        private const val SALT_REGION = 7103L
        private const val SALT_PILLAR = 7104L
        private const val SALT_WALL = 7105L
        private const val SALT_DOOR = 7106L
        private const val SALT_MEZZ = 7107L
        private const val SALT_CEILING = 7108L
        private const val SALT_LIGHT = 7109L
        private const val SALT_FURNITURE = 7110L

        private val PLANKS = listOf(
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS,
            Material.DARK_OAK_PLANKS,
            Material.MANGROVE_PLANKS,
            Material.CHERRY_PLANKS
        )

        private val LOGS = listOf(
            Material.OAK_LOG,
            Material.STRIPPED_OAK_LOG,
            Material.SPRUCE_LOG,
            Material.STRIPPED_SPRUCE_LOG,
            Material.DARK_OAK_LOG,
            Material.STRIPPED_DARK_OAK_LOG
        )

        private val FENCES = listOf(
            Material.OAK_FENCE,
            Material.SPRUCE_FENCE,
            Material.BIRCH_FENCE,
            Material.DARK_OAK_FENCE
        )

        private val PLATES = listOf(
            Material.OAK_PRESSURE_PLATE,
            Material.SPRUCE_PRESSURE_PLATE,
            Material.BIRCH_PRESSURE_PLATE
        )

        private val STAIR_WOODS = listOf(
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.BIRCH_PLANKS
        )

        private val BEDS = listOf(
            Material.CYAN_BED,
            Material.WHITE_BED,
            Material.RED_BED,
            Material.BROWN_BED,
            Material.LIGHT_GRAY_BED
        )

        private val FACES = arrayOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST)

        private val STONE_MIX = listOf(
            Material.STONE,
            Material.ANDESITE,
            Material.GRAVEL,
            Material.DIORITE,
            Material.GRANITE
        )
    }
}
