package io.github.zzzyyylllty.chotentech.function.body

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.data.type.Light
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

object BodyHealthManager {
    @Config("body-health.yml")
    lateinit var settings: Configuration
    private var task: PlatformExecutor.PlatformTask? = null
    private val moved = mutableSetOf<UUID>()
    private val sprinted = mutableSetOf<UUID>()
    private val fakeLights = mutableMapOf<UUID, List<Location>>()
    private val hpKey = NamespacedKey("chotentech", "body_health")
    private val speedKey = NamespacedKey("chotentech", "body_legs")

    @Awake(LifeCycle.ACTIVE)
    fun start() { task = submit(period = 5) { tick() } }

    @Awake(LifeCycle.DISABLE)
    fun stop() {
        task?.cancel()
        Bukkit.getOnlinePlayers().forEach { player ->
            restoreLights(player)
            modifier(player, Attribute.MAX_HEALTH, hpKey, 0.0)
            modifier(player, Attribute.MOVEMENT_SPEED, speedKey, 0.0)
        }
        moved.clear(); sprinted.clear()
    }

    fun reset(player: Player) {
        player.getData()?.let {
            it.bodyHealth = PlayerBodyHealth()
            it.statusEffects.clear()
            syncMaxima(player, it.bodyHealth)
        }
        modifier(player, Attribute.MAX_HEALTH, hpKey, 0.0)
        modifier(player, Attribute.MOVEMENT_SPEED, speedKey, 0.0)
        restoreLights(player)
        moved.remove(player.uniqueId); sprinted.remove(player.uniqueId)
    }

    fun syncMaxima(player: Player, body: PlayerBodyHealth) {
        body.parts.values.forEach { part ->
            val max = AttributeManager.getAttributeValue(player, "toughness_${part.id.lowercase()}_max").coerceAtLeast(.001)
            // Destroyed parts must stay at zero: resize() re-derives toughness from the old ratio.
            if (max != part.maxToughness) {
                if (part.destroyed) {
                    part.maxToughness = max
                    part.toughness = 0.0
                } else part.resize(max)
            }
        }
        val max = AttributeManager.getAttributeValue(player, "water_max").coerceAtLeast(.001)
        if (max != body.maxWater) {
            body.water = body.water / body.maxWater.coerceAtLeast(.001) * max
            body.maxWater = max
        }
    }

    /** Called by the lethal interceptor after armor/attribute damage and before the lethal check. */
    @Suppress("DEPRECATION")
    fun mitigate(event: EntityDamageEvent, player: Player) {
        if (event.isCancelled || player.isDead || player.gameMode in setOf(GameMode.CREATIVE, GameMode.SPECTATOR)) return
        if (event.cause.name in settings.getStringList("bypass-causes")) return
        val body = player.getData()?.bodyHealth ?: return
        syncMaxima(player, body)
        val absorbed = if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
            event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION) else 0.0
        val damage = event.finalDamage - absorbed
        if (!damage.isFinite() || damage <= 0) return
        val weights = distribution(event, player)
        // Snapshot every part's mitigation BEFORE any part/overflow is damaged.
        val hpDamage = weights.entries.sumOf { (id, weight) ->
            damage * weight * (1.0 - (body.parts[id]?.percentage ?: 0.0).coerceAtMost(.75))
        }
        weights.forEach { (id, weight) ->
            val amount = damage * weight
            val part = body.parts[id]
            if (amount > 10 && part?.effects?.containsKey(WOUND) == true) body.applyEffect(id, MAJOR_BLEED)
            body.damageToughness(id, amount)
        }
        // Preserve the post-armor result exactly; do not run armor's nonlinear formula twice.
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, hpDamage)
        EntityDamageEvent.DamageModifier.entries.filter { it != EntityDamageEvent.DamageModifier.BASE }
            .filter { event.isApplicable(it) }.forEach { event.setDamage(it, 0.0) }
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
            event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION, -minOf(player.absorptionAmount, hpDamage))
        syncPenalties(player, body)
    }

    fun distribution(event: EntityDamageEvent, player: Player): Map<String, Double> {
        val custom = settings.getConfigurationSection("damage-distribution.${event.cause.name}")
        if (custom != null) {
            val weights = custom.getKeys(false).mapNotNull { id ->
                val value = custom.getDouble(id)
                if (id.uppercase() in BodyParts.defaults && value.isFinite() && value > 0) id.uppercase() to value else null
            }.toMap()
            val total = weights.values.sum()
            if (total > 0) return weights.mapValues { it.value / total }
        }
        val gunPoint = player.getMetadata("chotentech_body_hit").firstOrNull()?.value() as? Location
        if (gunPoint != null) return mapOf(hitPart(player, gunPoint) to 1.0)
        if (event is EntityDamageByEntityEvent) {
            val attacker = event.damager as? LivingEntity
            if (attacker != null) {
                val eye = attacker.eyeLocation
                val intersection = player.boundingBox.rayTrace(eye.toVector(), eye.direction, 16.0)?.hitPosition
                if (intersection != null) return mapOf(hitPart(player, intersection.toLocation(player.world)) to 1.0)
            } else if (event.damager is org.bukkit.entity.Projectile) {
                return mapOf(hitPart(player, event.damager.location) to 1.0)
            }
        }
        return mapOf("BODY" to 1.0)
    }

    /** Height normalized to the current pose; lateral sides follow the victim's yaw. */
    fun hitPart(target: LivingEntity, point: Location): String {
        val box = target.boundingBox
        val height = (point.y - box.minY) / box.height.coerceAtLeast(.1)
        val delta = point.toVector().subtract(target.location.toVector())
        val yaw = Math.toRadians(target.location.yaw.toDouble())
        val side = delta.x * cos(yaw) + delta.z * kotlin.math.sin(yaw)
        return when {
            height >= .80 -> "HEAD"
            height < .40 -> if (side < 0) "RLEG" else "LLEG"
            kotlin.math.abs(side) > .20 -> if (side < 0) "RARM" else "LARM"
            height < .58 -> "STOMACH"
            else -> "BODY"
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun shockImmunity(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (BleedoutManager.isShocked(player.uniqueId)) event.isCancelled = true
    }

    fun armPenalties(player: Player): ArmPenalties = player.getData()?.bodyHealth?.armPenalties(
        settings.getDouble("pain.arm-aim-cost", .10), settings.getDouble("pain.arm-accuracy-penalty", .10))
        ?: ArmPenalties(0.0, 0.0, 0.0, 0.0)

    fun sprintBlocked(body: PlayerBodyHealth): Boolean = !body.hasEffect(PAIN_RELIEF) &&
        listOf("LLEG", "RLEG").any { id -> body.parts.getValue(id).let {
            it.destroyed || it.effects.containsKey(FRACTURE) || it.effects.containsKey(PAIN)
        } }

    @SubscribeEvent(ignoreCancelled = true)
    fun sprint(event: PlayerToggleSprintEvent) {
        if (event.isSprinting && event.player.getData()?.bodyHealth?.let { sprintBlocked(it) } == true) event.isCancelled = true
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun move(event: PlayerMoveEvent) {
        if (!event.hasExplicitlyChangedPosition()) return
        moved.add(event.player.uniqueId)
        if (event.player.isSprinting) sprinted.add(event.player.uniqueId)
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun jump(event: com.destroystokyo.paper.event.player.PlayerJumpEvent) {
        val body = event.player.getData()?.bodyHealth ?: return
        if (BleedoutManager.isShocked(event.player.uniqueId)) return
        legStrain(event.player, body, true)
        woundBleed(body, "wound.jump-bleed-chance", .1)
    }

    /**
     * 骨折 / 损毁腿部的「用力」代价。
     *
     * 韧性：每跳 0.5（骨折+损毁 1.0）；奔跑每秒 0.2（骨折+损毁 0.4）。
     * 血量：**直接扣**，每跳每腿 -1，奔跑每腿每秒 -0.5；骨折+损毁**不**额外翻倍。
     *
     * 两条扣减互相独立：扣掉的韧性不会再折算成血量（韧性本身从不直伤），
     * 血量这部分也不走护甲 / 韧性减伤公式，就是干净的直伤。
     * 左右腿各自结算，所以两条腿都有伤时数值叠加。
     */
    private fun legStrain(player: Player, body: PlayerBodyHealth, jump: Boolean) {
        for (id in listOf("LLEG", "RLEG")) {
            val part = body.parts.getValue(id)
            val fracture = part.effects.containsKey(FRACTURE)
            if (!part.destroyed && !fracture) continue
            // Fracture or destruction costs 0.5 per jump / 0.2 per second of sprinting; the
            // combined state is not an extra multiplier. Destroyed legs absorb nothing, so
            // damageToughness spreads the full cost at that leg's 0.6 multiplier.
            val fracturedAndDestroyed = part.destroyed && fracture
            val cost = if (jump) .5 else .2
            body.damageToughness(id, if (fracturedAndDestroyed) cost * 2 else cost)
            trueDamage(player, if (jump) 1.0 else .5)
        }
    }

    private fun woundBleed(body: PlayerBodyHealth, path: String, default: Double) {
        body.parts.values.filter { it.effects.containsKey(WOUND) }.forEach {
            if (Random.nextDouble() < settings.getDouble(path, default).coerceIn(0.0, 1.0))
                body.applyEffect(it.id, MINOR_BLEED)
        }
    }

    private fun tick() {
        for (player in Bukkit.getOnlinePlayers()) {
            // 单个玩家出错不能打断整轮：tickPlayer 里会碰传送 / 音效 / 假光源 / 属性读写，
            // 一次异常会让该轮剩余玩家的韧性、水分与惩罚全部漏同步，且 5 tick 后重复。
            try {
                tickPlayer(player)
            } catch (e: Exception) {
                devLog("[Body] 肢体韧性 tick 异常（${player.name}）: ${e.message}")
            }
        }
    }

    private fun tickPlayer(player: Player) {
        if (player.isDead || player.gameMode in setOf(GameMode.CREATIVE, GameMode.SPECTATOR)) return
        val data = playerDataMap[player.uniqueId.toString()] ?: return
        val body = data.bodyHealth
        body.normalize()
        // Existing non-localized effects become head effects; legacy fractures are mapped to legs.
        if (data.statusEffects.isNotEmpty()) {
            data.statusEffects.toList().forEach { effect ->
                val ids = if (effect.type == FRACTURE) listOf("LLEG", "RLEG") else listOf("HEAD")
                ids.forEach { body.applyEffect(it, effect.type, effect.level, effect.remainingDuration) }
            }
            data.statusEffects.clear()
        }
        syncMaxima(player, body)
        if (BleedoutManager.isShocked(player.uniqueId)) {
            restoreLights(player)
            return
        }
        body.onlineTicks += 5
        val second = body.onlineTicks % 20 == 0L
        val fiveSeconds = body.onlineTicks % 100 == 0L
        val helper = ChoTenDataHelper(player)
        for (part in body.parts.values) {
            part.effects.values.forEach { if (it.remainingDuration != Long.MAX_VALUE) it.remainingDuration -= 250 }
            part.effects.values.removeAll { it.remainingDuration <= 0 }
            // Bleeding only drains the part that already carries it; the effect is never
            // re-applied here, so a bleed-out tick cannot spread itself across the body.
            if (fiveSeconds) {
                if (part.effects.containsKey(MINOR_BLEED)) body.damageToughness(part.id, .6)
                if (part.effects.containsKey(MAJOR_BLEED)) body.damageToughness(part.id, 1.0)
            }
        }
        if (second) {
            for (effect in body.parts.getValue("HEAD").effects.values.toList()) {
                when (effect.type) {
                    SANITY_REGEN -> helper.addSanity(effect.level * .2)
                    SANITY_DRAIN -> helper.removeSanity(effect.level * .5)
                    STAMINA_REGEN -> helper.addStamina(effect.level.toDouble())
                    STRESS_RESISTANCE -> helper.removeStress(effect.level.toDouble())
                    STRESS_IMMUNITY -> helper.setStress(0.0)
                    HOUND_VIRUS -> if (body.onlineTicks % 200 == 0L && !body.hasEffect(IMMUNITY))
                        trueDamage(player, effect.level.toDouble() * effect.level)
                    MUSCLE_WEAKNESS -> player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 30, (effect.level - 1).coerceAtLeast(0)))
                }
            }
            if (sprintBlocked(body)) player.isSprinting = false
            if (player.isSprinting) {
                legStrain(player, body, false)
                woundBleed(body, "wound.sprint-bleed-chance", .05)
            }
        }
        if (fiveSeconds) {
            if (moved.remove(player.uniqueId)) {
                body.water = (body.water - if (sprinted.remove(player.uniqueId))
                    settings.getDouble("water.sprint-consumption", 1.0) else settings.getDouble("water.walk-consumption", .5)).coerceAtLeast(0.0)
            }
            if (body.parts.getValue("STOMACH").destroyed) {
                data.customHunger = (data.customHunger - 2.5).coerceAtLeast(0.0)
                body.water = (body.water - 1.0).coerceAtLeast(0.0)
            }
        }
        dehydration(player, body)
        syncPenalties(player, body, 5)
        visuals(player, body)
    }

    private fun dehydration(player: Player, body: PlayerBodyHealth) {
        val head = body.parts.getValue("HEAD")
        if (body.water > 0) {
            body.dehydrationTicks = 0
            head.effects.remove(DEHYDRATION); head.effects.remove(SEVERE_DEHYDRATION)
            return
        }
        body.dehydrationTicks += 5
        val severe = body.dehydrationTicks > 3600
        head.effects.remove(if (severe) DEHYDRATION else SEVERE_DEHYDRATION)
        val type = if (severe) SEVERE_DEHYDRATION else DEHYDRATION
        // applyEffect 每次都会 new PlayerStatusEffect 并写 map，而这里是每 5 tick 一次。
        // 效果已经在身上时不必重复构造（原实现是无条件重写一遍）。
        if (head.effects[type] == null) body.applyEffect("HEAD", type)
        if (severe && (head.destroyed || body.parts.getValue("BODY").destroyed)) {
            BleedoutManager.forceDeath(player)
        } else if (severe && (body.dehydrationTicks - 3600) % 100 == 0) trueDamage(player, 4.0)
        else if (!severe && body.dehydrationTicks % 40 == 0) trueDamage(player, 2.0)
    }

    /** No armor or toughness, but ordinary lethal true damage still enters the downed stages. */
    fun trueDamage(player: Player, amount: Double) {
        if (player.isDead || BleedoutManager.isShocked(player.uniqueId) || amount <= 0) return
        if (BleedoutManager.isDowned(player.uniqueId)) {
            BleedoutManager.damageDowned(player)
        } else if (player.health <= amount) BleedoutManager.enterBleedout(player)
        else player.health -= amount
    }

    fun syncPenalties(player: Player, body: PlayerBodyHealth, elapsedTicks: Int = 0) {
        for (id in listOf("HEAD", "BODY")) body.parts.getValue(id).tickDestructionPenalty(elapsedTicks)
        val (healthPenalty, speedPenalty) = computePenalties(body)
        modifier(player, Attribute.MAX_HEALTH, hpKey, -healthPenalty.coerceAtMost(.99))
        modifier(player, Attribute.MOVEMENT_SPEED, speedKey, -speedPenalty.coerceIn(0.0, .95))
    }

    /**
     * [syncPenalties] 使用的纯计算部分（不推进任何计时器），返回 (血量上限惩罚, 移动速度惩罚)。
     * 调试输出也走这里，保证展示值与真正生效的值不会各写一套而漂移。
     */
    fun computePenalties(body: PlayerBodyHealth): Pair<Double, Double> {
        var healthPenalty = 0.0
        for (id in listOf("HEAD", "BODY")) healthPenalty += body.parts.getValue(id).destructionPenalty
        val penetrations = body.parts.values.count { it.effects.containsKey(PENETRATION) }
        if (penetrations > 0) healthPenalty += when (penetrations) { 1 -> .08; 2 -> .12; else -> .16 }
        if (body.hasEffect(SHOCK_SEQUELA)) healthPenalty += .20
        var speedPenalty = 0.0
        for (id in listOf("LLEG", "RLEG")) {
            val part = body.parts.getValue(id)
            // Pain relief suppresses fracture effects; destruction still slows movement.
            speedPenalty += when {
                part.destroyed -> .20
                part.effects.containsKey(FRACTURE) && !body.hasEffect(PAIN_RELIEF) -> .15
                part.effects.containsKey(PAIN) && !body.hasEffect(PAIN_RELIEF) -> painLegSpeedPenalty()
                else -> 0.0
            }
        }
        return healthPenalty to speedPenalty
    }

    /** 配置未加载时（例如纯单元测试）回落到默认值，避免调试输出抛异常。 */
    private fun painLegSpeedPenalty(): Double =
        if (this::settings.isInitialized) settings.getDouble("pain.leg-speed-penalty", .10) else .10

    private fun modifier(player: Player, attribute: Attribute, key: NamespacedKey, value: Double) {
        val instance = player.getAttribute(attribute) ?: return
        val existing = instance.modifiers.firstOrNull { it.key == key }
        if ((existing?.amount ?: 0.0) == value) return
        existing?.let { instance.removeModifier(it) }
        if (value != 0.0) instance.addModifier(AttributeModifier(key, value, AttributeModifier.Operation.MULTIPLY_SCALAR_1))
        if (attribute == Attribute.MAX_HEALTH && player.health > instance.value) player.health = instance.value.coerceAtLeast(.01)
    }

    private fun visuals(player: Player, body: PlayerBodyHealth) {
        val head = body.parts.getValue("HEAD")
        val filterActive = head.effects.containsKey(BLACKOUT) || head.effects.containsKey(FLASH)
        val cycle = settings.getInt("filter.cycle-ticks", 100).coerceAtLeast(10)
        if (!filterActive) { body.filterValue = 0.0; body.filterTicks = 0; body.filterDelay = 0 }
        else if (body.filterDelay > 0) { body.filterDelay -= 5; body.filterValue = 0.0 }
        else {
            body.filterTicks += 5
            body.filterValue = (25.0 * (1 - cos(2 * PI * body.filterTicks.coerceAtMost(cycle) / cycle))).coerceIn(0.0, 50.0)
            if (body.filterTicks >= cycle) {
                body.filterTicks = 0
                val min = settings.getInt("filter.delay-min-ticks", 20).coerceAtLeast(0)
                val max = settings.getInt("filter.delay-max-ticks", 120).coerceAtLeast(min)
                body.filterDelay = Random.nextInt(min, max + 1)
            }
        }
        if ((head.effects.containsKey(TINNITUS) || head.effects.containsKey(FLASH)) &&
            body.onlineTicks % settings.getInt("tinnitus.interval-ticks", 20).coerceAtLeast(5) == 0L) {
            player.playSound(player.location, settings.getString("tinnitus.sound") ?: "minecraft:block.note_block.bit",
                settings.getDouble("tinnitus.volume", .5).toFloat(), settings.getDouble("tinnitus.pitch", 1.8).toFloat())
        }
        if (body.hasEffect(TREMOR) && Random.nextDouble() < settings.getDouble("tremor.chance", .25)) {
            val amplitude = settings.getDouble("tremor.degrees", .6).coerceAtLeast(0.0)
            // 只改朝向：teleport 到原地会触发 PlayerTeleportEvent（可能被领地/反作弊取消），
            // 还会重置坠落距离与速度、把玩家踢下载具。setRotation 只发朝向包，副作用最小。
            player.setRotation(
                (player.location.yaw + Random.nextDouble(-1.0, 1.0) * amplitude).toFloat(),
                (player.location.pitch + Random.nextDouble(-1.0, 1.0) * amplitude).toFloat().coerceIn(-90f, 90f)
            )
        }
        restoreLights(player)
        if (head.effects.containsKey(DIZZINESS)) {
            val range = settings.getInt("dizziness.radius", 4).coerceIn(1, 16)
            val light = Material.LIGHT.createBlockData() as Light
            light.level = 15
            val locations = (0 until settings.getInt("dizziness.blocks", 12).coerceIn(0, 128)).mapNotNull {
                val loc = player.eyeLocation.clone().add(Random.nextInt(-range, range + 1).toDouble(),
                    Random.nextInt(-range, range + 1).toDouble(), Random.nextInt(-range, range + 1).toDouble()).block.location
                if (loc.blockY in player.world.minHeight until player.world.maxHeight && loc.block.type.isAir) loc else null
            }.distinct()
            locations.forEach { player.sendBlockChange(it, light) }
            fakeLights[player.uniqueId] = locations
        }
    }

    private fun restoreLights(player: Player) {
        fakeLights.remove(player.uniqueId)?.forEach { if (it.world == player.world && it.world.isChunkLoaded(it.blockX shr 4, it.blockZ shr 4))
            player.sendBlockChange(it, it.block.blockData) }
    }

    @SubscribeEvent
    fun quit(event: PlayerQuitEvent) {
        restoreLights(event.player)
        moved.remove(event.player.uniqueId); sprinted.remove(event.player.uniqueId)
    }
}
