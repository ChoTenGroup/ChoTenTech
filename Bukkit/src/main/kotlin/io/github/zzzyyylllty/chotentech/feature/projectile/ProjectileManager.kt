package io.github.zzzyyylllty.chotentech.feature.projectile

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.damage.DamageManager
import io.github.zzzyyylllty.chotentech.damage.DamageType
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.function.player.getPlayerLevel
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.GameMode
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.World
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.submit
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ProjectileManager {

    // ==================== Config Registry ====================

    private val projectileConfigs = ConcurrentHashMap<String, ProjectileData>()

    // ==================== Active Tracking ====================

    private val activeProjectiles = ConcurrentHashMap<UUID, ActiveProjectile>()

    // ==================== 温雷 (Cooking) Tracking ====================

    data class CookingGrenade(
        val id: String,
        val data: ProjectileData,
        val player: Player,
        val visual: ItemStack,
        var fuseTicks: Int
    )

    private val cookingPlayers = ConcurrentHashMap<UUID, CookingGrenade>()

    private const val THROW_SPEED = 1.5

    // 默认音效兜底
    private val DEFAULT_THROW = SoundConfig(key = "entity.snowball.throw", volume = 1.0f, pitch = 1.2f)
    private val DEFAULT_BOUNCE = SoundConfig(key = "entity.item.break", volume = 0.5f, pitch = 1.8f)
    private val DEFAULT_EXPLOSION = SoundConfig(key = "entity.generic.explode", volume = 3.0f, pitch = 0.7f)
    private val DEFAULT_FRAGMENT = SoundConfig(key = "entity.item.break", volume = 3.0f, pitch = 1.5f)

    /** 每 N tick 播放一次 fuse 音效 */
    private const val FUSE_SOUND_INTERVAL = 10

    class ActiveProjectile(
        val id: String,
        val data: ProjectileData,
        val thrower: Player,
        var fuseTicks: Int,
        val entity: Item,
        var lastVelLength: Double = THROW_SPEED,
        var bounceCount: Int = 0,
        var tick: Int = 0,
        val maxTicks: Int = 300  // 15秒强制超时兜底
    )

    // ==================== YAML Loading ====================

    /**
     * 从 projectiles/ 目录加载投掷物 YAML 配置
     */
    fun loadProjectileFiles() {
        projectileConfigs.clear()
        val dir = File(getDataFolder(), "projectiles")
        if (!dir.exists()) {
            releaseResourceFolder("projectiles")
        }

        val files = dir.listFiles { f -> f.extension in listOf("yml", "yaml") }
        if (files.isNullOrEmpty()) return

        for (file in files) {
            val config = Configuration.loadFromFile(file)
            for (key in config.getKeys(false)) {
                try {
                    val section = config.getConfigurationSection(key) ?: continue
                    val fuseRaw = section.get("fuse")
                    val fuseConfig: FuseConfig = when (fuseRaw) {
                        is Number -> FuseConfig.Fixed(fuseRaw.toDouble())
                        is String -> {
                            val parts = fuseRaw.split("~")
                            if (parts.size == 2) {
                                val min = parts[0].trim().toDoubleOrNull() ?: 3.0
                                val max = parts[1].trim().toDoubleOrNull() ?: 3.0
                                FuseConfig.Range(min, max)
                            } else {
                                FuseConfig.Fixed(fuseRaw.toDoubleOrNull() ?: 3.0)
                            }
                        }
                        else -> FuseConfig.Fixed(3.0)
                    }
                    val impact = section.getBoolean("impact", false)

                    val bounceSection = section.getConfigurationSection("bounce")
                    val bounce = if (bounceSection != null) {
                        BounceConfig(
                            max = bounceSection.getInt("max", 5),
                            factor = bounceSection.getDouble("factor", 0.6),
                            upward = bounceSection.getDouble("upward", 0.2)
                        )
                    } else {
                        BounceConfig()
                    }

                    val soundsSection = section.getConfigurationSection("sounds")
                    val sounds = mutableMapOf<String, SoundConfig>()
                    if (soundsSection != null) {
                        for (sk in soundsSection.getKeys(false)) {
                            val ss = soundsSection.getConfigurationSection(sk) ?: continue
                            val key = ss.getString("sound") ?: continue
                            sounds[sk] = SoundConfig(
                                key = key,
                                volume = ss.getDouble("volume", 1.0).toFloat(),
                                pitch = ss.getDouble("pitch", 1.0).toFloat()
                            )
                        }
                    }

                    val particlesSection = section.getConfigurationSection("particles")
                    val particles = mutableMapOf<String, ParticleConfig>()
                    if (particlesSection != null) {
                        for (pk in particlesSection.getKeys(false)) {
                            val ps = particlesSection.getConfigurationSection(pk) ?: continue
                            val key = ps.getString("particle") ?: continue
                            particles[pk] = ParticleConfig(
                                key = key,
                                count = ps.getInt("count", 1),
                                speed = ps.getDouble("speed", 0.0),
                                offsetX = ps.getDouble("offset_x", 0.0),
                                offsetY = ps.getDouble("offset_y", 0.0),
                                offsetZ = ps.getDouble("offset_z", 0.0)
                            )
                        }
                    }

                    val effectsList = mutableListOf<ProjectileEffect>()
                    val effectsRaw = section.getList("effects") ?: emptyList<Any>()
                    for (obj in effectsRaw) {
                        val map = obj as? Map<*, *> ?: continue
                        val type = map["type"] as? String ?: continue
                        @Suppress("UNCHECKED_CAST")
                        val damageMap = (map["damage"] as? Map<String, Any>)?.mapValues {
                            (it.value as? Number)?.toDouble() ?: 0.0
                        } ?: emptyMap()

                        val effectSound = (map["sound"] as? Map<*, *>)?.let { sm ->
                            val sk = sm["sound"] as? String ?: return@let null
                            SoundConfig(
                                key = sk,
                                volume = (sm["volume"] as? Number)?.toFloat() ?: 1.0f,
                                pitch = (sm["pitch"] as? Number)?.toFloat() ?: 1.0f
                            )
                        }

                        val effectParticle = (map["particle"] as? Map<*, *>)?.let { pm ->
                            val pk = pm["particle"] as? String ?: return@let null
                            ParticleConfig(
                                key = pk,
                                count = (pm["count"] as? Number)?.toInt() ?: 1,
                                speed = (pm["speed"] as? Number)?.toDouble() ?: 0.0,
                                offsetX = (pm["offset_x"] as? Number)?.toDouble() ?: 0.0,
                                offsetY = (pm["offset_y"] as? Number)?.toDouble() ?: 0.0,
                                offsetZ = (pm["offset_z"] as? Number)?.toDouble() ?: 0.0
                            )
                        }

                        val shockwaveSection = map["shockwave"] as? Map<*, *>
                        val shockwave = shockwaveSection?.let { sw ->
                            ShockwaveConfig(
                                particleKey = (sw["particle"] as? String) ?: "glow",
                                count = (sw["count"] as? Number)?.toInt() ?: 36,
                                speed = (sw["speed"] as? Number)?.toDouble() ?: 1.0,
                                rings = (sw["rings"] as? Number)?.toInt() ?: 3
                            )
                        }

                        val penetration = (map["penetration"] as? Number)?.toDouble() ?: 0.0

                        val falloffSection = map["falloff"] as? Map<*, *>
                        val falloff = falloffSection?.let { fo ->
                            FalloffConfig(
                                enabled = (fo["enabled"] as? Boolean) ?: true,
                                start = (fo["start"] as? Number)?.toDouble() ?: 0.0,
                                end = (fo["end"] as? Number)?.toDouble() ?: (map["range"] as? Number)?.toDouble() ?: 30.0,
                                minMult = (fo["min_mult"] as? Number)?.toDouble() ?: 0.1,
                                formula = (fo["formula"] as? String) ?: "linear"
                            )
                        }

                        effectsList.add(
                            ProjectileEffect(
                                type = type,
                                range = (map["range"] as? Number)?.toDouble() ?: 0.0,
                                seconds = (map["seconds"] as? Number)?.toInt() ?: 0,
                                amount = (map["amount"] as? Number)?.toInt() ?: 0,
                                damage = damageMap,
                                power = (map["power"] as? Number)?.toInt() ?: 0,
                                effectId = (map["effect"] as? String) ?: "",
                                level = (map["level"] as? Number)?.toInt() ?: 1,
                                sound = effectSound,
                                particle = effectParticle,
                                check = (map["check"] as? Boolean) ?: false,
                                shockwave = shockwave,
                                penetration = penetration,
                                falloff = falloff
                            )
                        )
                    }

                    projectileConfigs[key] = ProjectileData(
                        id = key,
                        fuse = fuseConfig,
                        impact = impact,
                        bounce = bounce,
                        sounds = sounds,
                        particles = particles,
                        effects = effectsList
                    )
                } catch (_: Exception) {
                    // skip invalid entry
                }
            }
        }
    }

    fun getProjectile(id: String): ProjectileData? = projectileConfigs[id]
    fun hasProjectile(id: String): Boolean = projectileConfigs.containsKey(id)

    // ==================== Throwing ====================

    /**
     * 投掷投掷物。生成掉落物实体作为视觉表现，赋予速度，加入追踪。
     */
    fun throwProjectile(player: Player, projectileId: String, visual: ItemStack = ItemStack(Material.GUNPOWDER)) {
        val data = projectileConfigs[projectileId] ?: return
        ensureTrackingTask()

        val eyeLoc = player.eyeLocation
        val direction = eyeLoc.direction.normalize()

        playSoundAt(player.world, eyeLoc, data.sounds["throw"], DEFAULT_THROW)
        spawnParticlesAt(player.world, eyeLoc, data.particles["throw"])

        val entity = player.world.dropItem(eyeLoc, visual).apply {
            velocity = direction.multiply(THROW_SPEED)
            setCanPlayerPickup(false)
            setCanMobPickup(false)
            pickupDelay = Int.MAX_VALUE
            isInvulnerable = true
        }

        val active = ActiveProjectile(
            id = projectileId,
            data = data,
            thrower = player,
            fuseTicks = (data.fuse.rollSeconds() * 20).toInt(),
            entity = entity
        )

        activeProjectiles[entity.uniqueId] = active
    }

    // ==================== 温雷 (Cooking) ====================

    fun isCooking(player: Player): Boolean = cookingPlayers.containsKey(player.uniqueId)

    fun startCooking(player: Player, projectileId: String, item: ItemStack) {
        val data = projectileConfigs[projectileId] ?: return
        ensureTrackingTask()

        cookingPlayers[player.uniqueId] = CookingGrenade(
            id = projectileId,
            data = data,
            player = player,
            visual = item.clone().apply { amount = 1 },
            fuseTicks = (data.fuse.rollSeconds() * 20).toInt()
        )

        val eyeLoc = player.eyeLocation
        spawnParticlesAt(player.world, eyeLoc, data.particles["cook"])
        playSoundAt(player.world, eyeLoc, data.sounds["cook"])
        player.sendMessage("§e⚡ 已拉环! 引信正在燃烧...")
    }

    fun throwCookedProjectile(player: Player, consumedItem: ItemStack? = null) {
        val cooking = cookingPlayers.remove(player.uniqueId) ?: return
        val data = cooking.data

        // 消耗物品（创造模式不消耗）
        if (consumedItem != null && player.gameMode != GameMode.CREATIVE) {
            consumedItem.amount--
            player.updateInventory()
        }

        // 补偿0.4s (8 ticks) 保证玩家能投掷出去
        val remainingFuse = maxOf(cooking.fuseTicks + 8, 1)

        val eyeLoc = player.eyeLocation
        val direction = eyeLoc.direction.normalize()

        playSoundAt(player.world, eyeLoc, data.sounds["throw"], DEFAULT_THROW)
        spawnParticlesAt(player.world, eyeLoc, data.particles["throw"])

        val entity = player.world.dropItem(eyeLoc, cooking.visual).apply {
            velocity = direction.multiply(THROW_SPEED)
            setCanPlayerPickup(false)
            setCanMobPickup(false)
            pickupDelay = Int.MAX_VALUE
            isInvulnerable = true
        }

        activeProjectiles[entity.uniqueId] = ActiveProjectile(
            id = cooking.id,
            data = data,
            thrower = player,
            fuseTicks = remainingFuse,
            entity = entity
        )
    }

    fun cancelCooking(player: Player, showMessage: Boolean = true) {
        val cooking = cookingPlayers.remove(player.uniqueId) ?: return
        if (showMessage) player.sendMessage("§7已取消投掷")
        val eyeLoc = player.eyeLocation
        playSoundAt(player.world, eyeLoc, cooking.data.sounds["cancel"])
        spawnParticlesAt(player.world, eyeLoc, cooking.data.particles["cancel"])
    }

    // ==================== Tick Tracking ====================

    private var tracking = false

    private fun ensureTrackingTask() {
        if (tracking) return
        tracking = true
        submit(period = 1L) {
            tickAll()
        }
    }

    private fun tickAll() {
        // 飞行中的投掷物
        if (activeProjectiles.isNotEmpty()) {
            tickActiveProjectiles()
        }
        // 手中温雷
        if (cookingPlayers.isNotEmpty()) {
            tickCookingPlayers()
        }
    }

    private fun tickActiveProjectiles() {
        val iter = activeProjectiles.entries.iterator()
        while (iter.hasNext()) {
            val (_, active) = iter.next()
            val entity = active.entity

            // 实体被移除 / 死亡
            if (!entity.isValid || entity.isDead) {
                executeEffects(entity.location, active)
                iter.remove()
                continue
            }

            // 最大存活时间兜底：防止实体卡住无限生成粒子
            if (active.tick >= active.maxTicks) {
                executeEffects(entity.location, active)
                iter.remove()
                continue
            }

            val data = active.data

            // 落地碰撞
            if (entity.isOnGround) {
                entity.velocity = entity.velocity.setY(0.0)
                active.lastVelLength = entity.velocity.length()
                if (data.impact) {
                    executeEffects(entity.location, active)
                    iter.remove()
                    continue
                }
            }

            // 撞墙检测：速度骤降表示撞到方块
            val currentSpeed = entity.velocity.length()
            if (!entity.isOnGround && active.lastVelLength > 0.3 && currentSpeed < 0.2) {
                if (data.impact) {
                    executeEffects(entity.location, active)
                    iter.remove()
                    continue
                } else if (active.bounceCount < data.bounce.max) {
                    // 弹墙反弹
                    playSoundAt(entity.world, entity.location, data.sounds["bounce"], DEFAULT_BOUNCE)
                    spawnParticlesAt(entity.world, entity.location, data.particles["bounce"])
                    entity.velocity = entity.velocity.multiply(-data.bounce.factor)
                        .add(Vector(0.0, data.bounce.upward, 0.0))
                    active.bounceCount++
                }
                active.lastVelLength = entity.velocity.length()
            } else {
                active.lastVelLength = currentSpeed
            }

            // 引信 tick 音效
            active.tick++
            val fuseSound = data.sounds["fuse"]
            if (fuseSound != null && active.tick % FUSE_SOUND_INTERVAL == 0) {
                playSoundAt(entity.world, entity.location, fuseSound)
                spawnParticlesAt(entity.world, entity.location, data.particles["fuse"])
            }

            // 飞行轨迹粒子
            spawnParticlesAt(entity.world, entity.location, data.particles["trail"])

            // 引信倒计时
            active.fuseTicks--
            if (active.fuseTicks <= 0) {
                executeEffects(entity.location, active)
                iter.remove()
                continue
            }

            // 安全检测：超出世界边界
            val loc = entity.location
            if (loc.y < -64 || loc.y > 320) {
                executeEffects(loc, active)
                iter.remove()
            }
        }
    }

    private fun tickCookingPlayers() {
        val iter = cookingPlayers.entries.iterator()
        while (iter.hasNext()) {
            val (_, cooking) = iter.next()
            val player = cooking.player

            if (!player.isOnline || player.isDead) {
                iter.remove()
                continue
            }

            // 手上物品检查：如果玩家不再持有该投掷物则取消温雷
            val mainHandId = player.inventory.itemInMainHand.let {
                if (it.type.isAir) null else Sertraline.api().getId(it)
            }
            if (mainHandId != cooking.id) {
                player.sendMessage("§7已取消投掷")
                iter.remove()
                continue
            }

            // 递减引信
            cooking.fuseTicks--

            val loc = player.eyeLocation

            // 引信音效/粒子 (每10 tick)
            if (cooking.fuseTicks % FUSE_SOUND_INTERVAL == 0) {
                val fuseSound = cooking.data.sounds["fuse"]
                if (fuseSound != null) playSoundAt(player.world, loc, fuseSound)
                spawnParticlesAt(player.world, loc, cooking.data.particles["fuse"])
            }

            // 持续 cook 粒子（手部黑烟，统一覆盖配置中的 flame）
            spawnParticlesAt(player.world, loc, ParticleConfig("smoke", 8, 0.3))

            // 引信耗尽 → 手中爆炸
            if (cooking.fuseTicks <= 0) {
                iter.remove()
                player.sendMessage("§c💥 引信燃尽! 手雷在手中爆炸!")
                applyEffects(player.location, cooking.data, player)
                player.damage(1000.0)
            }
        }
    }

    // ==================== Effects ====================

    private fun executeEffects(location: Location, active: ActiveProjectile) {
        try { if (active.entity.isValid) active.entity.remove() } catch (_: Exception) {}
        applyEffects(location, active.data, active.thrower)
    }

    private fun applyEffects(location: Location, data: ProjectileData, thrower: Player) {
        val world = location.world ?: return

        // === 通用爆炸特效：所有投掷物爆炸时都有 ===
        spawnDetonationFlash(world, location)
        // === 碎片散射 ===
        spawnBurstParticles(world, location, ParticleConfig("campfire_cosy_smoke", 12, 0.3))
        spawnBurstParticles(world, location, ParticleConfig("flame", 8, 0.4))

        for (effect in data.effects) {
            // 冲击波环
            if (effect.shockwave != null) {
                spawnShockwaveRings(world, location, effect.shockwave!!)
            }

            when (effect.type) {
                "explosion" -> {
                    world.createExplosion(location, effect.power.toFloat(), false, false)
                    playSoundAt(world, location, effect.sound, DEFAULT_EXPLOSION)
                    spawnParticlesAt(world, location, effect.particle)
                }
                "fragment" -> {
                    val fragmentCount = effect.amount.coerceAtLeast(1)
                    val physicalDmg = effect.damage["physical"] ?: 0.0

                    devLog("[破片] 爆炸于 ${location.blockX},${location.blockY},${location.blockZ} world=${location.world.name} range=${effect.range} count=$fragmentCount thrower=${thrower.name} at ${thrower.location.blockX},${thrower.location.blockY},${thrower.location.blockZ}")

                    val nearby = world.getNearbyEntities(location, effect.range, effect.range, effect.range)
                        .filterIsInstance<LivingEntity>()
                        .filter { !(it is Player && isPlayerInDmz(it)) }
                    devLog("[破片] 范围内非DMZ实体: ${nearby.size}个 (${nearby.map { "${it.name}@${it.location.blockX},${it.location.blockY},${it.location.blockZ}" }})")

                    if (nearby.isNotEmpty()) {
                        // 强制命中过的实体 → 随机破片不再重复伤害
                        val forcedHit = mutableSetOf<LivingEntity>()

                        // === 强制命中：随距离 0~30% 破片锁定目标 ===
                        for (entity in nearby) {
                            val distance = location.distance(entity.location)
                            if (effect.check && entity is Player) {
                                if (!shouldAffectPlayer(entity, location)) continue
                            }
                            // 视线检测
                            val toTarget = entity.location.toVector().subtract(location.toVector())
                            val dir2 = toTarget.normalize()
                            val blockHit = world.rayTraceBlocks(location, dir2, distance, FluidCollisionMode.NEVER, true)
                            if (blockHit != null && blockHit.hitBlock != null) continue

                            val forcePct = (1.0 - distance / effect.range).coerceIn(0.0, 1.0) * 0.3
                            val mult = effect.falloff?.multiplier(distance) ?: 1.0
                            val totalMult = mult * (1.0 + forcePct)

                            if (physicalDmg > 0) entity.damage(physicalDmg * totalMult, thrower)
                            // 穿透伤害直接改血量，但玩家已倒地则跳过（否则会覆盖倒地系统的1点血量）
                            if (effect.penetration > 0 && !BleedoutManager.isDowned(entity.uniqueId)) {
                                val penetrationDmg = effect.penetration * totalMult
                                entity.health = maxOf(0.0, entity.health - penetrationDmg)
                            }
                            if (entity is Player) {
                                val pData = ChoTenTech.playerDataMap[entity.uniqueId.toString()] ?: continue
                            }
                            forcedHit.add(entity)
                            devLog("[破片] 强制命中 ${entity.name} dist=${"%.1f".format(distance)} 强制率=${"%.0f".format(forcePct*100)}% falloff=$mult totalMult=$totalMult")
                        }

                        val hitEntities = mutableSetOf<LivingEntity>()
                        var blockStops = 0
                        var noHits = 0
                        for (i in 0 until fragmentCount) {
                            // 大部分破片朝四周发射，少量向上
                            val angle = Math.random() * 2 * Math.PI
                            val upAngle = Math.random() * Math.PI / 4  // 0~45° 仰角
                            val dir = Vector(
                                Math.cos(angle) * Math.cos(upAngle),
                                Math.sin(upAngle),
                                Math.sin(angle) * Math.cos(upAngle)
                            )
                            val result = world.rayTrace(location, dir, effect.range, FluidCollisionMode.NEVER, false, 0.5) {
                                it is LivingEntity && !(it is Player && isPlayerInDmz(it))
                            }
                            if (result == null) { noHits++; continue }
                            if (result.hitBlock != null) { blockStops++; continue }
                            val hit = result.hitEntity as? LivingEntity ?: continue
                            if (effect.check && hit is Player) {
                                if (!shouldAffectPlayer(hit, location)) continue
                            }
                            hitEntities.add(hit)
                        }
                        devLog("[破片] 方块拦截=$blockStops/$fragmentCount 无命中=$noHits/${fragmentCount} 不重复实体=${hitEntities.size}个")
                        for (entity in hitEntities) {
                            if (entity in forcedHit) continue
                            val distance = location.distance(entity.location)
                            val mult = effect.falloff?.multiplier(distance) ?: 1.0

                            if (physicalDmg > 0) entity.damage(physicalDmg * mult, thrower)
                            // 穿透伤害跳过已倒地玩家（防覆盖倒地血量）
                            if (effect.penetration > 0 && !BleedoutManager.isDowned(entity.uniqueId)) {
                                val penetrationDmg = effect.penetration * mult
                                entity.health = maxOf(0.0, entity.health - penetrationDmg)
                            }
                            if (entity is Player) {
                                val pData = ChoTenTech.playerDataMap[entity.uniqueId.toString()] ?: continue
                            }
                            devLog("[破片] 命中 ${entity.name} dist=${"%.2f".format(distance)} falloff=$mult")
                        }
                    }

                    playSoundAt(world, location, effect.sound, DEFAULT_FRAGMENT)
                    spawnParticlesAt(world, location, effect.particle)
                    if (effect.particle != null) {
                        spawnBurstParticles(world, location, effect.particle!!, 20)
                    }
                }
                "blindness" -> {
                    val entities = world.getNearbyEntities(location, effect.range, effect.range, effect.range)
                    for (entity in entities) {
                        if (entity !is LivingEntity) continue
                        if (entity is Player && isPlayerInDmz(entity)) continue
                        if (effect.check && entity is Player) {
                            if (!shouldAffectPlayer(entity, location)) continue
                        }
                        entity.addPotionEffect(
                            PotionEffect(PotionEffectType.BLINDNESS, effect.seconds * 20, 1)
                        )
                    }
                    playSoundAt(world, location, effect.sound)
                    spawnParticlesAt(world, location, effect.particle)
                }
                "potion" -> {
                    val potionType = PotionEffectType.getByName(effect.effectId) ?: continue
                    val entities = world.getNearbyEntities(location, effect.range, effect.range, effect.range)
                    for (entity in entities) {
                        if (entity !is LivingEntity) continue
                        if (entity is Player && isPlayerInDmz(entity)) continue
                        if (effect.check && entity is Player) {
                            if (!shouldAffectPlayer(entity, location)) continue
                        }
                        entity.addPotionEffect(
                            PotionEffect(potionType, effect.seconds * 20, effect.level - 1)
                        )
                    }
                    playSoundAt(world, location, effect.sound)
                    spawnParticlesAt(world, location, effect.particle)
                }
                "lightning_strike" -> {
                    val strikeCount = effect.amount.coerceAtLeast(1)
                    val searchRange = effect.range.coerceAtLeast(1.0)
                    val aoeRadius = effect.power.coerceAtLeast(2)
                    val shockDmg = effect.damage["shock"] ?: 0.0
                    val burnDmg = effect.damage["burn"] ?: 0.0

                    // 落雷目标：排除投掷者，否则雷会全劈向投掷者，怪物完全接不到伤害
                    // （投掷者仍会作为 victims 受到范围自伤）
                    val nearbyEntities = world.getNearbyEntities(location, searchRange, searchRange, searchRange)
                        .filterIsInstance<LivingEntity>()
                        .filter { it.isValid }
                        .filter { it != thrower }
                        .filter { !(it is Player && isPlayerInDmz(it)) }

                    val strikePositions = mutableListOf<Location>()

                    if (nearbyEntities.isNotEmpty()) {
                        val sorted = nearbyEntities.sortedBy { it.location.distanceSquared(location) }
                        val targetCount = minOf(strikeCount, sorted.size + 2)
                        for (i in 0 until targetCount) {
                            val entity = sorted[i.coerceAtMost(sorted.size - 1)]
                            val ox = (Math.random() - 0.5) * aoeRadius * 1.5
                            val oz = (Math.random() - 0.5) * aoeRadius * 1.5
                            strikePositions.add(entity.location.clone().add(ox, 0.0, oz))
                        }
                        while (strikePositions.size < strikeCount) {
                            val theta = Math.random() * 2 * Math.PI
                            val r = Math.random() * searchRange
                            strikePositions.add(location.clone().add(r * Math.cos(theta), 0.0, r * Math.sin(theta)))
                        }
                    } else {
                        for (i in 0 until strikeCount) {
                            val theta = Math.random() * 2 * Math.PI
                            val r = Math.random() * searchRange
                            strikePositions.add(location.clone().add(r * Math.cos(theta), 0.0, r * Math.sin(theta)))
                        }
                    }

                    // 依次执行雷击,每次间隔 1 秒
                    strikePositions.forEachIndexed { index, strikeLoc ->
                        submit(delay = index * 20L) {
                        val groundLoc = strikeLoc.clone().apply {
                            y = world.getHighestBlockYAt(this).toDouble()
                        }
                        world.strikeLightningEffect(groundLoc)

                        // includes thrower (self-damage)
                        val victims = world.getNearbyEntities(groundLoc, aoeRadius.toDouble(), aoeRadius.toDouble(), aoeRadius.toDouble())
                            .filterIsInstance<LivingEntity>()
                            .filter { it.isValid }
                            .filter { !(it is Player && isPlayerInDmz(it)) }

                        for (victim in victims) {
                            if (shockDmg > 0) {
                                val finalShock = DamageManager.calculateDamage(shockDmg, DamageType.SHOCK, victim)
                                victim.damage(finalShock, thrower)
                                if (victim is Player) {
                                    DamageManager.applyEffects(victim, DamageType.SHOCK, finalShock, shockDmg)
                                }
                            }
                            if (burnDmg > 0) {
                                val finalBurn = DamageManager.calculateDamage(burnDmg, DamageType.BURN, victim)
                                victim.damage(finalBurn, thrower)
                                if (victim is Player) {
                                    DamageManager.applyEffects(victim, DamageType.BURN, finalBurn, burnDmg)
                                }
                            }
                            // 电击眩晕效果
                            if (victim is Player) {
                                victim.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 40, 0))
                                victim.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 60, 0))
                            }
                        }

                        // 小范围爆炸效果（仅视觉，无伤害/方块破坏）
                        world.createExplosion(groundLoc, 0f, false, false)

                        // electric spark burst at each strike point
                        resolveParticle("electric_spark")?.let { p ->
                            world.spawnParticle(p, groundLoc, 20, 1.5, 0.5, 1.5, 0.3)
                        }
                        resolveParticle("glow")?.let { p ->
                            world.spawnParticle(p, groundLoc, 10, 0.8, 0.5, 0.8, 0.05)
                        }
                        resolveParticle("cloud")?.let { p ->
                            world.spawnParticle(p, groundLoc, 8, 0.5, 0.3, 0.5, 0.02)
                        }
                        resolveParticle("smoke")?.let { p ->
                            world.spawnParticle(p, groundLoc, 12, 1.0, 0.3, 1.0, 0.01)
                        }

                        // expanding electric ring at each strike
                        resolveParticle("electric_spark")?.let { p ->
                            for (ring in 1..3) {
                                val radius = ring * 1.0
                                val steps = (ring * 8).coerceAtLeast(6)
                                for (i in 0 until steps) {
                                    val angle = i * 2.0 * Math.PI / steps
                                    val x = groundLoc.x + radius * Math.cos(angle)
                                    val z = groundLoc.z + radius * Math.sin(angle)
                                    world.spawnParticle(p, x, groundLoc.y + 0.3, z, 0, 0.0, 0.0, 0.0, 0.15)
                                }
                            }
                        }
                    }
                    }

                    // central thunder sound
                    world.playSound(location, "entity.lightning_bolt.thunder", 4.0f, 0.6f)
                    playSoundAt(world, location, effect.sound)
                    spawnParticlesAt(world, location, effect.particle)
                }
                "fireburst" -> {
                    val radius = effect.range.coerceAtLeast(2.0)
                    val fireSeconds = effect.amount.coerceAtLeast(3)
                    val fireTicks = fireSeconds * 20
                    val burnDmg = effect.damage["burn"] ?: 0.0

                    // visual center flash + explosion (no block damage)
                    resolveParticle("flash")?.let { world.spawnParticle(it, location, 1, 0.0, 0.0, 0.0, 0.0) }
                    resolveParticle("explosion_emitter")?.let { world.spawnParticle(it, location, 1, 0.0, 0.0, 0.0, 0.0) }

                    // massive flame burst
                    resolveParticle("flame")?.let { p ->
                        world.spawnParticle(p, location, 100, radius, radius * 0.8, radius, 0.4)
                    }

                    // expanding fire rings
                    resolveParticle("flame")?.let { p ->
                        for (ring in 1..4) {
                            val r = ring * (radius / 4)
                            val steps = (ring * 16).coerceAtLeast(12)
                            for (i in 0 until steps) {
                                val angle = i * 2.0 * Math.PI / steps
                                val x = location.x + r * Math.cos(angle)
                                val z = location.z + r * Math.sin(angle)
                                val yOffset = Math.sin(angle * 2) * 0.5
                                world.spawnParticle(p, x, location.y + 0.5 + yOffset, z, 0, 0.0, 0.0, 0.0, 0.2)
                            }
                        }
                    }

                    // lava embers
                    resolveParticle("lava")?.let { p ->
                        world.spawnParticle(p, location, 30, radius * 0.6, 0.5, radius * 0.6, 0.0)
                        // random ember sprays
                        for (i in 0 until 15) {
                            val theta = Math.random() * 2 * Math.PI
                            val phi = Math.random() * Math.PI
                            val dist = Math.random() * radius
                            val x = location.x + dist * Math.sin(phi) * Math.cos(theta)
                            val z = location.z + dist * Math.sin(phi) * Math.sin(theta)
                            world.spawnParticle(p, x, location.y + 0.3, z, 0, 0.0, 0.0, 0.0, 0.05)
                        }
                    }

                    // smoke column
                    resolveParticle("large_smoke")?.let { p ->
                        world.spawnParticle(p, location, 50, radius * 0.5, radius * 0.4, radius * 0.5, 0.06)
                    }
                    resolveParticle("cloud")?.let { p ->
                        world.spawnParticle(p, location, 35, radius * 0.4, radius * 0.2, radius * 0.4, 0.03)
                    }
                    resolveParticle("campfire_cosy_smoke")?.let { p ->
                        world.spawnParticle(p, location, 20, radius * 0.3, 0.1, radius * 0.3, 0.01)
                    }

                    // persistent fire zone: 每秒检测半径内的实体，持续 fireSeconds
                    val center = location.clone()
                    var zoneTick = 0
                    // 同一火区对每个玩家只触发一次整套灼烧DoT，
                    // 避免每秒重复调用 applyEffects -> BurnHandler 互相 cancel 导致灼烧失效
                    val burnedThisIncident = mutableSetOf<UUID>()
                    submit(period = 20L, delay = 0L) {
                        if (zoneTick >= fireSeconds) return@submit
                        zoneTick++

                        // 每秒火焰粒子
                        resolveParticle("flame")?.let { p ->
                            world.spawnParticle(p, center, 20, radius * 0.5, 0.5, radius * 0.5, 0.05)
                        }
                        resolveParticle("small_flame")?.let { p ->
                            world.spawnParticle(p, center, 30, radius * 0.7, 0.4, radius * 0.7, 0.08)
                        }
                        resolveParticle("lava")?.let { p ->
                            world.spawnParticle(p, center, 8, radius * 0.6, 0.3, radius * 0.6, 0.02)
                        }
                        resolveParticle("campfire_cosy_smoke")?.let { p ->
                            world.spawnParticle(p, center, 16, radius * 0.4, 0.8, radius * 0.4, 0.04)
                        }
                        resolveParticle("large_smoke")?.let { p ->
                            world.spawnParticle(p, center, 10, radius * 0.3, 0.3, radius * 0.3, 0.03)
                        }

                        // 点燃范围内实体
                        val nearby = world.getNearbyEntities(center, radius, radius, radius)
                        for (entity in nearby) {
                            if (entity !is LivingEntity) continue
                            if (entity is Player && isPlayerInDmz(entity)) continue
                            if (effect.check && entity is Player) {
                                if (!shouldAffectPlayer(entity, center)) continue
                            }
                            entity.fireTicks = maxOf(entity.fireTicks, 40)
                            if (burnDmg <= 0) continue
                            if (entity is Player) {
                                // 整场火灾只结算一次整套灼烧DoT，总伤害=burnDmg（BurnHandler 每秒 finalDamage/4、共4秒）
                                // 避免把 burnDmg 平均到 amount 秒得到亚 1 的分数伤害（被护甲/防御取整后 ≈0，看不掉血）
                                if (burnedThisIncident.add(entity.uniqueId)) {
                                    DamageManager.applyEffects(entity, DamageType.BURN, burnDmg, burnDmg)
                                }
                            } else {
                                // 非玩家实体：仍按持续时间分摊
                                entity.damage(burnDmg / fireSeconds, thrower)
                            }
                        }
                    }

                    // ground fire patches (visual)
                    resolveParticle("flame")?.let { p ->
                        for (i in 0 until (radius * 6).toInt().coerceAtMost(40)) {
                            val theta = Math.random() * 2 * Math.PI
                            val dist = Math.random() * radius * 0.8
                            val x = location.x + dist * Math.cos(theta)
                            val z = location.z + dist * Math.sin(theta)
                            world.spawnParticle(p, x, location.y + 0.1, z, 0, 0.0, 0.0, 0.0, 0.08)
                        }
                    }

                    // big fire roar
                    world.playSound(location, "entity.firework_rocket.large_blast", 5.0f, 0.5f)
                    world.playSound(location, "block.lava.extinguish", 3.0f, 0.3f)
                    playSoundAt(world, location, effect.sound)
                    spawnParticlesAt(world, location, effect.particle)
                }
                "smokescreen" -> {
                    val radius = effect.range.coerceAtLeast(3.0)
                    val invisTicks = (effect.seconds * 20).coerceAtLeast(20)
                    val particleCount = effect.amount.coerceAtLeast(50)

                    // apply invisibility to nearby entities
                    val nearby = world.getNearbyEntities(location, radius, radius, radius)
                    for (entity in nearby) {
                        if (entity !is LivingEntity || entity == thrower) continue
                        if (entity is Player && isPlayerInDmz(entity)) continue
                        if (effect.check && entity is Player) {
                            if (!shouldAffectPlayer(entity, location)) continue
                        }
                        entity.addPotionEffect(
                            PotionEffect(PotionEffectType.INVISIBILITY, invisTicks, 0, true, false)
                        )
                    }

                    // center flash
                    resolveParticle("flash")?.let { world.spawnParticle(it, location, 1, 0.0, 0.0, 0.0, 0.0) }
                    resolveParticle("explosion_emitter")?.let { world.spawnParticle(it, location, 1, 0.0, 0.0, 0.0, 0.0) }

                    // massive campfire_signal_smoke outward burst (layered rings)
                    resolveParticle("campfire_signal_smoke")?.let { p ->
                        // dense radial burst
                        world.spawnParticle(p, location, particleCount, radius, radius * 0.6, radius, 0.3)

                        // expanding ring layers for a rolling smoke effect
                        for (ring in 1..5) {
                            val r = ring * (radius / 5)
                            val steps = (ring * 12).coerceAtLeast(8)
                            for (i in 0 until steps) {
                                val angle = i * 2.0 * Math.PI / steps
                                val x = location.x + r * Math.cos(angle)
                                val z = location.z + r * Math.sin(angle)
                                val yOff = 0.2 + Math.random() * radius * 0.5
                                world.spawnParticle(p, x, location.y + yOff, z, 0, 0.0, 0.0, 0.0, 0.12)
                            }
                        }

                        // rising smoke columns
                        for (i in 0 until (radius * 4).toInt().coerceAtMost(24)) {
                            val theta = Math.random() * 2 * Math.PI
                            val dist = Math.random() * radius * 0.7
                            val x = location.x + dist * Math.cos(theta)
                            val z = location.z + dist * Math.sin(theta)
                            val yBase = location.y + Math.random() * radius * 0.4
                            world.spawnParticle(p, x, yBase, z, 0, 0.0, 0.0, 0.0, 0.08)
                        }
                    }

                    // complementary campfire_cosy_smoke for depth
                    resolveParticle("campfire_cosy_smoke")?.let { p ->
                        world.spawnParticle(p, location, (particleCount * 0.4).toInt(), radius * 0.6, radius * 0.3, radius * 0.6, 0.15)
                    }

                    // cloud particles for density
                    resolveParticle("cloud")?.let { p ->
                        world.spawnParticle(p, location, (particleCount * 0.3).toInt(), radius * 0.5, radius * 0.2, radius * 0.5, 0.05)
                    }

                    // smoke for trailing wisps
                    resolveParticle("smoke")?.let { p ->
                        world.spawnParticle(p, location, (particleCount * 0.2).toInt(), radius * 0.8, radius * 0.3, radius * 0.8, 0.02)
                    }

                    // muffled thump
                    world.playSound(location, "entity.firework_rocket.blast", 2.0f, 0.3f)
                    playSoundAt(world, location, effect.sound)
                    spawnParticlesAt(world, location, effect.particle)
                }
            }
        }
    }

    // ==================== Facing Check Helper ====================

    /**
     * 检测玩家是否正对着指定位置且没有被不透明方块遮挡。
     * 用于 flashbang / fragment 等效果的躲避判定。
     */
    private fun shouldAffectPlayer(player: Player, from: Location): Boolean {
        val eyeLoc = player.eyeLocation
        val toExplosion = from.toVector().subtract(eyeLoc.toVector())
        val distance = toExplosion.length()
        if (distance <= 0.0) return true
        val norm = toExplosion.normalize()
        if (eyeLoc.direction.normalize().dot(norm) < 0.0) return false
        val hit = player.world.rayTraceBlocks(eyeLoc, norm, distance, FluidCollisionMode.NEVER, true)
        return hit == null || hit.hitBlock == null
    }

    // ==================== DMZ Check ====================

    /**
     * 检测玩家是否处于非军事区（DMZ），处于 DMZ 的玩家不受手雷伤害
     */
    private fun isPlayerInDmz(player: Player): Boolean = player.getPlayerLevel()?.dmz == true

    // ==================== Sound Helper ====================

    /**
     * 播放音效。优先用 config，没有则 fallback，都没有则跳过。
     */
    private fun playSoundAt(world: World, loc: Location, config: SoundConfig? = null, fallback: SoundConfig? = null) {
        val sc = config ?: fallback ?: return
        if (sc.key.isBlank()) return
        world.playSound(loc, sc.key.lowercase(), sc.volume, sc.pitch)
    }

    // ==================== Particle Helpers ====================

    private fun resolveParticle(key: String): org.bukkit.Particle? =
        Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key))

    private fun spawnParticlesAt(world: World, loc: Location, config: ParticleConfig?) {
        if (config == null) return
        val particle = resolveParticle(config.key) ?: return
        try {
            world.spawnParticle(particle, loc, config.count, config.offsetX, config.offsetY, config.offsetZ, config.speed)
        } catch (_: Exception) {
            // 某些粒子（如 vibration）需要特殊 data，忽略
        }
    }

    /**
     * 爆炸通用特效：强闪光 + 大型爆炸轮廓 + 烟雾 + 轰鸣声
     */
    private fun spawnDetonationFlash(world: World, location: Location) {
        resolveParticle("flash")?.let { world.spawnParticle(it, location, 1, 0.0, 0.0, 0.0, 0.0) }
        resolveParticle("explosion_emitter")?.let { world.spawnParticle(it, location, 1, 0.0, 0.0, 0.0, 0.0) }
        resolveParticle("large_smoke")?.let { world.spawnParticle(it, location, 30, 1.0, 1.0, 1.0, 0.1) }
        resolveParticle("cloud")?.let { world.spawnParticle(it, location, 20, 0.5, 1.0, 0.5, 0.05) }
        world.playSound(location, "entity.generic.explode", 3.0f, 0.7f)
    }

    /**
     * 生成多环扩散冲击波粒子
     */
    private fun spawnShockwaveRings(world: World, center: Location, config: ShockwaveConfig) {
        val particle = resolveParticle(config.particleKey) ?: return
        for (ring in 1..config.rings) {
            val radius = ring * 1.2
            val count = (config.count * ring / config.rings).coerceAtLeast(4)
            val step = 2.0 * Math.PI / count
            val y = center.y + 0.3
            for (i in 0 until count) {
                val angle = i * step
                val x = center.x + radius * Math.cos(angle)
                val z = center.z + radius * Math.sin(angle)
                world.spawnParticle(particle, x, y, z, 0, 0.0, 0.0, 0.0, config.speed)
            }
        }
    }

    /**
     * 随机方向散射粒子（碎片飞溅）
     */
    private fun spawnBurstParticles(world: World, center: Location, config: ParticleConfig, extraCount: Int = 0) {
        val particle = resolveParticle(config.key) ?: return
        val count = (config.count + extraCount).coerceAtLeast(1)
        for (i in 0 until count) {
            val vx = (Math.random() - 0.5) * 2
            val vy = (Math.random() - 0.5) * 2
            val vz = (Math.random() - 0.5) * 2
            world.spawnParticle(particle, center, 0, vx, vy, vz, config.speed)
        }
    }

    // ==================== Cleanup ====================

    fun cleanupAll() {
        activeProjectiles.values.forEach { it.entity.remove() }
        activeProjectiles.clear()
    }
}
