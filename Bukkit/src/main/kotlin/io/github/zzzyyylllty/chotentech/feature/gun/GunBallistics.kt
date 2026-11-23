package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 一发飞行中的子弹。
 *
 * - [pos] 当前位置（世界坐标，每次子步推进）
 * - [velocity] 速度（格 / tick）
 * - [traveled] 已飞行距离（格），伤害衰减与射程判定都用它
 * - [pierceLeft] 剩余可穿透次数（打穿方块或用掉一次实体命中都消耗 1，归零回收）
 * - [speedLoss] / [damageLoss] 每次穿透的速度/伤害损失比例（穿透配置的快照）
 * - [damage]**（P1 追加）** 当前伤害基数，穿透时按 [damageLoss] 衰减，命中时交给 [GunDamage]
 */
data class Bullet(
    var pos: Location,
    var velocity: Vector,
    var traveled: Double,
    val ammo: AmmoData,
    val gun: GunData,
    val owner: UUID,
    var pierceLeft: Int,
    val speedLoss: Double,
    val damageLoss: Double,
    var damage: Double,
    /** 同一发子弹对同一实体只结算一次，即使穿透后仍停留在其碰撞箱内。 */
    val hitEntityIds: MutableSet<UUID> = mutableSetOf(),
)

/**
 * 纯服务端弹道模拟（不生成实体，不做客户端预测）。
 *
 * 每 tick 由 [GunStateManager] 的全局 1 tick 循环调用一次 [tick]：
 * 1. `v.y -= gravity`，`v *= drag`
 * 2. `steps = ceil(|v| / sub-step).coerceAtMost(max-sub-steps)`，每子步推进 `|v| / steps`
 * 3. 子步内做**方块**检测（`rayTraceBlocks` + 碰撞箱 AABB 求厚度）
 * 4. 整 tick 位移上做**一次**实体检测（`getNearbyEntities` + 射线-AABB）
 *    —— 计划原文写的是「每子步查实体」，这里合并成整段一次：
 *    更准（连续线段判定不会漏掉高速子弹），也避免每 tick 每发子弹查 8 次实体表
 * 5. 每 tick 在当前位置打 1 个曳光粒子
 *
 * 线程：全部在主线程（由 GunStateManager 保证）。
 */
object GunBallistics {

    /** Spend one penetration allowance after a block/entity hit; zero means the bullet expires. */
    internal fun consumePierce(pierceLeft: Int): Int = (pierceLeft - 1).coerceAtLeast(0)

    // ==================== 诊断计数 ====================
    //
    // 「打不中实体 / 打了没伤害」这种问题靠猜没用：这里按链路分段计数，
    // `/ctt gun state` 会打印 —— 哪一段是 0 就断在哪一段。
    //   发射：实际生成了多少发子弹
    //   命中实体：射线扫到实体并进入结算的次数
    //   命中方块：打在方块上的次数
    //   结算生效：GunDamage 里 damage() 真正没被取消的次数

    @Volatile
    var spawned: Long = 0L
        private set

    @Volatile
    var entityHits: Long = 0L
        private set

    @Volatile
    var blockHits: Long = 0L
        private set

    /** 实体扫描盒在弹道线段外统一外扩的格数（子弹有半径，命中判定要比线段宽一点）。 */
    private const val ENTITY_BOX_MARGIN = 0.4


    private val bullets = ArrayList<Bullet>()

    /**
     * 允许被子弹打穿的「薄材质」名单，用 `name.contains` 判断以省掉长枚举列表。
     * 真正的判定还要叠加厚度与硬度（见 `gun-mechanism.yml` 的 `penetration` 段）。
     */
    private val PENETRABLE_KEYWORDS = listOf(
        "GLASS", "GLASS_PANE", "IRON_BARS", "LEAVES", "DOOR", "TRAPDOOR",
        "FENCE", "CARPET", "SNOW", "WEB", "VINE", "LADDER", "SIGN", "TORCH",
        "BUTTON", "LEVER", "FLOWER", "GRASS", "SAPLING", "CANDLE", "RAIL",
    )

    /** 当前飞行中的子弹数量（调试用）。 */
    fun size(): Int = bullets.size

    /**
     * 生成一发子弹。
     *
     * @param player 射手（用于排除自身命中 + 伤害归属）
     * @param origin 出膛点（世界坐标）
     * @param direction 单位方向向量
     * @param distance 已经飞过的距离（多弹丸武器可以预置，P1 恒为 0）
     */
    fun spawn(
        player: Player,
        origin: Location,
        direction: Vector,
        ammo: AmmoData,
        gun: GunData,
        distance: Double = 0.0,
    ) {
        spawn(player, origin, direction, ammo, gun, distance, 1.0)
    }

    /**
     * 生成一发子弹，并额外乘一个**弹速倍率**（P3：耐久磨损让弹速变慢）。
     *
     * 单独开一个重载而不是给 [spawn] 加默认参数，是为了不改动 P1 已定的公开签名：
     * 旧调用点（6 参）行为完全不变，P3 的开火路径走这个 7 参版本。
     *
     * @param speedMultiplier 速度倍率（1.0 = 原始弹速，`0.85 + 0.15 * 耐久比` 由 [GunShoot] 算好传入）
     */
    fun spawn(
        player: Player,
        origin: Location,
        direction: Vector,
        ammo: AmmoData,
        gun: GunData,
        distance: Double,
        speedMultiplier: Double,
    ) {
        val cfg = GunSettings.ballistics
        val speedMult = speedMultiplier.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val speedPerTick = (gun.attributes.bulletSpeed / 20.0) * ammo.speedMult * speedMult
        val dir = direction.clone().normalize()
        val velocity = dir.clone().multiply(speedPerTick)
        // 同时存在的子弹上限：超了就把最早的一发回收
        while (bullets.size >= cfg.maxBullets) {
            bullets.removeAt(0)
        }
        bullets += Bullet(
            pos = origin.clone(),
            velocity = velocity,
            traveled = distance,
            ammo = ammo,
            gun = gun,
            owner = player.uniqueId,
            pierceLeft = pierceCountOf(ammo),
            speedLoss = GunSettings.penetration.speedLoss,
            damageLoss = GunSettings.penetration.damageLoss,
            damage = ammo.damage,
        )
        spawned++
        devLog(
            "[Gun] 生成子弹 gun=${gun.id} ammo=${ammo.id} " +
                "伤害基数=${ammo.damage} 弹速=${"%.2f".format(speedPerTick)}/t " +
                "每发弹丸=${ammo.pellets} 起点=${origin.x.toInt()},${origin.y.toInt()},${origin.z.toInt()}",
        )
    }

    /** 穿透次数 = 1 + 穿甲值/50（穿甲 50 → 2 次，穿甲 100 → 3 次）。 */
    private fun pierceCountOf(ammo: AmmoData): Int =
        (1 + (ammo.penetration / 50.0).toInt()).coerceIn(1, 8)

    /** 清空所有子弹（关服 / 重载时调用）。 */
    fun clear() {
        bullets.clear()
    }

    // ==================== 每 tick 推进 ====================

    /**
     * 推进一 tick。
     *
     * 用**下标遍历**而不是快照 + `remove(bullet)`：`Bullet` 是 data class，
     * 两发状态完全相同的子弹会 `equals` 相等，用值比较删除会误删同型子弹。
     */
    fun tick() {
        if (bullets.isEmpty()) return
        var index = 0
        while (index < bullets.size) {
            val bullet = bullets[index]
            val remove = try {
                step(bullet)
            } catch (_: Exception) {
                true
            }
            if (remove) {
                bullets.removeAt(index)
            } else {
                index++
            }
        }
    }

    /** @return true = 这一发该回收了 */
    private fun step(bullet: Bullet): Boolean {
        val cfg = GunSettings.ballistics
        val world = bullet.pos.world ?: return true
        val maxRange = (bullet.gun.attributes.range * cfg.maxRangeMultiplier).coerceAtLeast(0.0)
        if (bullet.traveled >= maxRange) return true

        // 1) 重力 + 阻力
        bullet.velocity = Vector(
            bullet.velocity.x * cfg.drag,
            bullet.velocity.y * cfg.drag - cfg.gravity,
            bullet.velocity.z * cfg.drag,
        )

        val speed = bullet.velocity.length()
        if (speed < 1e-5) return true

        val steps = ceil(speed / cfg.subStep).toInt().coerceIn(1, cfg.maxSubSteps)
        val stepLen = speed / steps
        var dir = bullet.velocity.clone().normalize()

        for (stepIndex in 0 until steps) {
            val from = bullet.pos.clone()
            val remainingRange = maxRange - bullet.traveled
            if (remainingRange <= 1e-9) return true
            val segmentLength = minOf(stepLen, remainingRange)
            val to = from.clone().add(dir.clone().multiply(segmentLength))

            // Clamp entity and block traces to the remaining range so a hit beyond max range is impossible.
            val target = findEntityHit(bullet, from, dir, segmentLength)
            val hit = try {
                world.rayTraceBlocks(from, dir, segmentLength, FluidCollisionMode.NEVER, true)
            } catch (_: Exception) {
                null
            }
            val block = hit?.hitBlock
            val hitPos = hit?.hitPosition
            val blockDistance = if (hitPos != null) from.toVector().distance(hitPos) else Double.MAX_VALUE
            if (target != null && target.first < blockDistance) {
                val hitPoint = from.clone().add(dir.clone().multiply(target.first))
                bullet.hitEntityIds += target.second.uniqueId
                // 伤害衰减必须使用本次命中点的距离，而不是本 tick 开始时的累计距离。
                // 高速子弹可能在一个 tick 内飞过很长距离，否则近距离命中会被错误地按 0 距离结算。
                val hitDistance = bullet.traveled + target.first
                entityHits++
                devLog(
                    "[Gun] 命中实体 type=${target.second.type} 距离=${"%.2f".format(hitDistance)} " +
                        "伤害基数=${"%.2f".format(bullet.damage)} 穿甲=${bullet.ammo.penetration}",
                )
                GunDamage.applyHit(bullet, target.second, hitPoint, hitDistance)
                bullet.pierceLeft = consumePierce(bullet.pierceLeft)
                if (bullet.pierceLeft <= 0) return true
                // 推进到实体碰撞箱出口，避免下一 tick 从目标内部开始，
                // 把目标身后的墙误判为当前线段的直接命中。
                val entityRange = rayBoxRange(from.toVector(), dir, segmentLength, target.second.boundingBox)
                val exitDistance = (entityRange?.second ?: target.first) + 0.05
                val entityExit = from.clone().add(dir.clone().multiply(exitDistance.coerceAtMost(segmentLength)))
                bullet.traveled += from.toVector().distance(entityExit.toVector())
                bullet.pos = entityExit
                break
            }

            if (block != null && hitPos != null) {
                val thickness = thicknessOf(block, from.toVector(), dir, segmentLength)
                val canPierce = canPierce(block, thickness)
                if (canPierce && bullet.pierceLeft > 0) {
                    bullet.pierceLeft = consumePierce(bullet.pierceLeft)
                    bullet.velocity = bullet.velocity.multiply(1.0 - bullet.speedLoss)
                    bullet.damage *= (1.0 - bullet.damageLoss)
                    // 挪到方块另一侧，完成这次穿透，避免下一子步又打在同一面墙上。
                    val exit = hitPos.clone().add(dir.clone().multiply(thickness + 0.05))
                    val exitDistance = from.toVector().distance(exit)
                    if (exitDistance > remainingRange) return true
                    bullet.traveled += exitDistance
                    bullet.pos = exit.toLocation(world)
                    // 本次穿透已用尽次数：穿过方块后回收，不再命中墙后目标/继续穿透。
                    if (bullet.pierceLeft <= 0) return true
                    // 穿透后本 tick 不再推进（避免一 tick 连穿多堵墙）
                    break
                }
                // 打不穿：停在方块表面
                blockHits++
                playBlockHit(world, hitPos.toLocation(world))
                return true
            }

            // 3) 推进
            bullet.pos = to
            bullet.traveled += segmentLength

            // The clamped segment reaches maxRange exactly; expire immediately instead of creating an extra segment.
            if (bullet.traveled >= maxRange) return true
        }

        // 6) 曳光（每 tick 1 个）
        spawnTracer(world, bullet.pos)
        return false
    }

    // ==================== 方块穿透判定 ====================

    /**
     * 方块「厚度」：沿射线方向穿入到穿出的距离（格）。
     *
     * 用 `block.getCollisionShape().getBoundingBoxes()`（方块局部 0~1 坐标）平移到世界坐标后
     * 做线段-AABB 相交；取最近的那个碰撞箱的 (穿出 - 穿入) 作为厚度。
     * 没有碰撞箱（例如纯装饰方块）返回 0（等价于「无限薄」，可穿透）。
     */
    private fun thicknessOf(block: Block, origin: Vector, dir: Vector, len: Double): Double {
        val boxes = try {
            block.collisionShape.boundingBoxes
        } catch (_: Exception) {
            return 0.0
        }
        if (boxes.isEmpty()) return 0.0
        val ox = block.x.toDouble()
        val oy = block.y.toDouble()
        val oz = block.z.toDouble()

        var bestEntry = Double.MAX_VALUE
        var bestThickness = 0.0
        for (box in boxes) {
            val worldBox = BoundingBox(
                box.minX + ox, box.minY + oy, box.minZ + oz,
                box.maxX + ox, box.maxY + oy, box.maxZ + oz,
            )
            // 已命中该碰撞箱，完整求 entry→exit；不能用子步长截断出口，否则会重复穿同一方块。
            val range = rayBoxRange(origin, dir, maxOf(len, 8.0), worldBox) ?: continue
            if (range.first < bestEntry) {
                bestEntry = range.first
                bestThickness = (range.second - range.first).coerceAtLeast(0.0)
            }
        }
        return bestThickness
    }

    private fun canPierce(block: Block, thickness: Double): Boolean {
        val cfg = GunSettings.penetration
        val name = try {
            block.type.name
        } catch (_: Exception) {
            ""
        }
        val hardness = try {
            block.type.hardness.toDouble()
        } catch (_: Exception) {
            Double.MAX_VALUE
        }
        val thinEnough = thickness <= cfg.thin && hardness in 0.0..cfg.hardnessLimit
        if (thinEnough) return true
        return PENETRABLE_KEYWORDS.any { name.contains(it) } && hardness <= cfg.hardnessLimit
    }

    private fun playBlockHit(world: World, point: Location) {
        try {
            world.playSound(point, GunSettings.sounds.hitBlock, 1.0f, 1.2f)
            world.spawnParticle(Particle.CRIT, point, 2, 0.05, 0.05, 0.05)
        } catch (_: Exception) {
        }
    }

    // ==================== 实体命中判定 ====================

    /** 在 [from] 到 [from + dir*len] 这一段上找最近的实体命中，返回 (距离, 实体)。 */
    private fun findEntityHit(
        bullet: Bullet,
        from: Location,
        dir: Vector,
        len: Double,
    ): Pair<Double, LivingEntity>? {
        val world = from.world ?: return null
        // ⚠️ 不能用 `BoundingBox.of(corner1, corner2)`：Paper 的实现是把两个角的坐标**原样**
        // 当作 (minX,minY,minZ,maxX,maxY,maxZ)，**不做排序归一化**。
        // 子弹方向只要有负分量（往下打、往 -X / -Z 打），盒子就变成 min > max 的反向盒子，
        // `overlaps` 永远返回 false → 扫不到任何实体 → 「打了没伤害」。
        // 这里显式取两个角的 min/max 再扩 0.4 格。
        val a = from.toVector()
        val b = a.clone().add(dir.clone().multiply(len))
        val box = BoundingBox(
            minOf(a.x, b.x) - ENTITY_BOX_MARGIN,
            minOf(a.y, b.y) - ENTITY_BOX_MARGIN,
            minOf(a.z, b.z) - ENTITY_BOX_MARGIN,
            maxOf(a.x, b.x) + ENTITY_BOX_MARGIN,
            maxOf(a.y, b.y) + ENTITY_BOX_MARGIN,
            maxOf(a.z, b.z) + ENTITY_BOX_MARGIN,
        )
        val candidates: Collection<Entity> = try {
            world.getNearbyEntities(box) { entity ->
                entity is LivingEntity &&
                    entity.uniqueId != bullet.owner &&
                    entity.uniqueId !in bullet.hitEntityIds &&
                    !entity.isDead &&
                    entity.health > 0.0
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (candidates.isEmpty()) return null

        var bestT = Double.MAX_VALUE
        var best: LivingEntity? = null
        val origin = from.toVector()
        for (entity in candidates) {
            val living = entity as? LivingEntity ?: continue
            val range = rayBoxRange(origin, dir, len, living.boundingBox) ?: continue
            if (range.first < bestT) {
                bestT = range.first
                best = living
            }
        }
        val target = best ?: return null
        return bestT to target
    }

    // ==================== 曳光 ====================

    private fun spawnTracer(world: World, pos: Location) {
        try {
            world.spawnParticle(Particle.END_ROD, pos, 1, 0.0, 0.0, 0.0)
        } catch (_: Exception) {
        }
    }

    // ==================== 线段-AABB 相交 ====================

    /**
     * 射线 [origin] + [dir] * t（0 <= t <= len）与 [box] 的相交区间。
     *
     * @return (穿入距离, 穿出距离)；不相交返回 null（dir 必须是单位向量）
     */
    private fun rayBoxRange(
        origin: Vector,
        dir: Vector,
        len: Double,
        box: BoundingBox,
    ): Pair<Double, Double>? {
        val o = doubleArrayOf(origin.x, origin.y, origin.z)
        val d = doubleArrayOf(dir.x, dir.y, dir.z)
        val min = doubleArrayOf(box.minX, box.minY, box.minZ)
        val max = doubleArrayOf(box.maxX, box.maxY, box.maxZ)

        var tMin = 0.0
        var tMax = len
        for (axis in 0..2) {
            if (abs(d[axis]) < 1e-9) {
                if (o[axis] < min[axis] || o[axis] > max[axis]) return null
                continue
            }
            var t1 = (min[axis] - o[axis]) / d[axis]
            var t2 = (max[axis] - o[axis]) / d[axis]
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
            }
            if (t1 > tMin) tMin = t1
            if (t2 < tMax) tMax = t2
            if (tMin > tMax) return null
        }
        return tMin to tMax
    }
}
