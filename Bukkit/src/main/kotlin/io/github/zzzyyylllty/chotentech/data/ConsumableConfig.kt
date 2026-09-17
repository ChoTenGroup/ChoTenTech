package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.sertraline.Sertraline
import ink.ptms.um.Mythic
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.HashSet
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ConsumableEffect(
    val effect: String,
    val mode: String,           // "add", "set", or "percent"
    val value: Double = 0.0,
    // potion-specific
    val potionId: String? = null,
    val duration: Int = 0,      // ticks (potion) or seconds (status_effect)
    val amplifier: Int = 0,
    // sound-specific
    val sound: String? = null,
    val soundVolume: Float = 1.0f,
    val soundPitch: Float = 1.0f,
    // tick_task fields
    val taskDelay: Int = 0,     // initial delay in ticks
    val taskPeriod: Int = 0,    // repeat period (0 = run once)
    val taskRepeats: Int = 0,   // max executions (0 = infinite)
    val subEffects: List<ConsumableEffect> = emptyList(),
    // modifier-specific fields
    val modifierId: String? = null,
    val modifierAmount: Double = 0.0,
    val modifierType: ModifierType = ModifierType.ADD,
    val modifierSource: ModifierSource = ModifierSource.OTHER,
    val modifierSlot: AttributeSlot = AttributeSlot.OTHER,
    val modifierUuid: String? = null,
    val modifierExpire: Long? = null,  // millis, -1 = permanent
    // status_effect-specific
    val statusEffectId: String? = null,
    val statusEffectLevel: Int = 0,
    // conditional effects
    val thresholds: String = "",
    // modifier death mark
    val deathMark: Boolean = false,
    // random chance to trigger (percentage, 100 = always)
    val chance: Double = 100.0,
    // health 效果的回血上限(最大生命的百分比,0 = 无上限)
    val cap: Double = 0.0,
)

/** Track active tick_task PlatformTask references per player UUID. Cancelled on death. */
val consumableTaskIds = ConcurrentHashMap<String, MutableList<PlatformExecutor.PlatformTask>>()

fun cancelConsumableTasks(playerId: String) {
    consumableTaskIds.remove(playerId)?.forEach { task ->
        task.cancel()
    }
}

fun cancelAllConsumableTasks() {
    consumableTaskIds.values.forEach { tasks ->
        tasks.forEach { it.cancel() }
    }
    consumableTaskIds.clear()
}

data class ConsumableConfig(
    val id: String,
    val effects: List<ConsumableEffect>,
    val instant: Boolean = false,
    val medical: Boolean = false,
) {
    fun apply(player: Player) {
        val helper = ChoTenDataHelper(player)
        // Pre-cleanup: remove old modifiers sharing the same UUID so reapplication replaces, not stacks
        val seen = mutableSetOf<UUID>()
        for (ef in effects) {
            if (ef.effect == "modifier" && ef.modifierUuid != null) {
                val uuid = UUID.nameUUIDFromBytes(ef.modifierUuid!!.toByteArray())
                if (seen.add(uuid)) {
                    AttributeManager.removeModifierByUuid(player.uniqueId, uuid)
                }
            }
        }
        for (ef in effects) {
            applySingleEffect(player, helper, ef)
        }
    }

    companion object {
        fun applySingleEffect(player: Player, helper: ChoTenDataHelper, ef: ConsumableEffect) {
            // Random chance skip
            if (ef.chance < 100.0 && kotlin.random.Random.nextDouble() * 100 >= ef.chance) return
            when (ef.effect) {
                "health" -> {
                    val hp = player.health
                    val maxHp = player.maxHealth
                    val capHp = if (ef.cap > 0.0) maxHp * (ef.cap / 100.0) else maxHp
                    when (ef.mode) {
                        "add" -> {
                            val target = hp + ef.value
                            // 回血(正数)不高于 cap、不高于满血,且不低于当前血量(高于 cap 时不回也不降)
                            player.health = if (ef.value >= 0) {
                                maxOf(minOf(target, capHp, maxHp), hp)
                            } else {
                                target.coerceIn(0.0, maxHp)
                            }
                        }
                        "set" -> player.health = ef.value.coerceIn(0.0, minOf(capHp, maxHp))
                        "percent" -> {
                            val target = hp + maxHp * (ef.value / 100.0)
                            player.health = if (ef.value >= 0) {
                                maxOf(minOf(target, capHp, maxHp), hp)
                            } else {
                                target.coerceIn(0.0, maxHp)
                            }
                        }
                    }
                }
                "hunger" -> {
                    when (ef.mode) {
                        "add" -> helper.addHunger(ef.value)
                        "set" -> helper.setHunger(ef.value)
                    }
                }
                "saturation" -> {
                    when (ef.mode) {
                        "add" -> helper.addSaturation(ef.value)
                        "set" -> helper.setSaturation(ef.value)
                    }
                }
                "food_restore" -> {
                    when (ef.mode) {
                        "add" -> {
                            helper.addHunger(ef.value)
                            helper.addSaturation(ef.value)
                        }
                        "set" -> {
                            helper.setHunger(ef.value)
                            helper.setSaturation(ef.value)
                        }
                    }
                }
                "consumption" -> {
                    when (ef.mode) {
                        "add" -> helper.addConsumption(ef.value)
                        "set" -> helper.setConsumption(ef.value)
                    }
                }
                "sanity" -> {
                    when (ef.mode) {
                        "add" -> helper.addSanity(ef.value)
                        "set" -> helper.setSanity(ef.value)
                    }
                }
                "darkness" -> {
                    when (ef.mode) {
                        "add" -> helper.addDarkness(ef.value)
                        "set" -> helper.setDarkness(ef.value)
                    }
                }
                "external_injury", "externalinjury" -> {
                    when (ef.mode) {
                        "add" -> helper.addExternalInjury(ef.value)
                        "set" -> helper.setExternalInjury(ef.value)
                        "percent" -> helper.addExternalInjury(helper.data.maxExternalInjury * (ef.value / 100.0))
                    }
                }
                "internal_injury", "internalinjury" -> {
                    when (ef.mode) {
                        "add" -> helper.addInternalInjury(ef.value)
                        "set" -> helper.setInternalInjury(ef.value)
                        "percent" -> helper.addInternalInjury(helper.data.maxInternalInjury * (ef.value / 100.0))
                    }
                }
                "stress" -> {
                    when (ef.mode) {
                        "add" -> helper.addStress(ef.value)
                        "set" -> helper.setStress(ef.value)
                    }
                }
                "stamina" -> {
                    when (ef.mode) {
                        "add" -> helper.addStamina(ef.value)
                        "set" -> helper.setStamina(ef.value)
                    }
                }
                "hand_stamina", "handstamina" -> {
                    when (ef.mode) {
                        "add" -> helper.addHandStamina(ef.value)
                        "set" -> helper.setHandStamina(ef.value)
                    }
                }
                "status_effect" -> {
                    val seId = ef.statusEffectId ?: return
                    if (ef.duration > 0) {
                        helper.addStatusEffect(seId, ef.statusEffectLevel, ef.duration * 1000L)
                    }
                }
                "sanity_by_darkness" -> {
                    if (ef.thresholds.isNotBlank()) {
                        val amount = parseSanityByDarknessThresholds(ef.thresholds, helper.data.darkness)
                        if (amount != null) helper.addSanity(amount)
                    }
                }
                "potion" -> {
                    val type = PotionEffectType.getByName(ef.potionId ?: return) ?: return
                    player.addPotionEffect(PotionEffect(type, ef.duration, ef.amplifier, true, true, true))
                }
                "sound" -> {
                    val soundName = ef.sound ?: return
                    try {
                        val sound = org.bukkit.Sound.valueOf(soundName)
                        player.world.playSound(player.location, sound, SoundCategory.PLAYERS, ef.soundVolume, ef.soundPitch)
                    } catch (_: IllegalArgumentException) {
                        player.world.playSound(player.location, soundName, SoundCategory.PLAYERS, ef.soundVolume, ef.soundPitch)
                    }
                }
                "particle" -> {
                    val key = ef.potionId ?: return
                    val particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key)) ?: return
                    val count = ef.duration.coerceAtLeast(1)
                    val offset = ef.value
                    val speed = ef.amplifier.toDouble()
                    player.world.spawnParticle(particle, player.location, count, offset, offset, offset, speed)
                }
                "nutrition_fat" -> {
                    helper.data.fat = minOf(helper.data.fat + ef.value, 100.0)
                    io.github.zzzyyylllty.chotentech.data.NutritionManager.sync(player)
                }
                "counter_marshmallow" -> {
                    val counter = helper.data.marshmallowCounter + ef.value.toInt()
                    helper.data.marshmallowCounter = counter.coerceAtLeast(0)
                }
                "mm_skill" -> {
                    val skillName = ef.sound ?: return
                    Mythic.API.castSkill(
                        caster = player,
                        skillName = skillName,
                        trigger = player,
                        origin = player.location,
                        et = HashSet(),
                        lt = HashSet(),
                        power = ef.value.toFloat().coerceAtLeast(0.1f)
                    )
                }
                "modifier" -> {
                    val uuid = ef.modifierUuid?.let { UUID.nameUUIDFromBytes(it.toByteArray()) } ?: UUID.randomUUID()
                    val expireOn = if (ef.modifierExpire != null && ef.modifierExpire > 0) {
                        System.currentTimeMillis() + ef.modifierExpire
                    } else null
                    val mod = AttributeModifier(
                        uuid = uuid,
                        attributeId = ef.modifierId ?: return,
                        value = ef.modifierAmount,
                        type = ef.modifierType,
                        source = ef.modifierSource,
                        slot = ef.modifierSlot,
                        expireOn = expireOn,
                        deathMark = ef.deathMark
                    )
                    AttributeManager.addModifier(player.uniqueId, mod)
                }
                "tick_task" -> {
                    if (ef.subEffects.isEmpty()) return
                    var runs = 0
                    val task = submit(period = ef.taskPeriod.toLong().coerceAtLeast(1L), delay = ef.taskDelay.toLong()) {
                        if (ef.taskRepeats > 0 && runs >= ef.taskRepeats) {
                            this.cancel()
                            return@submit
                        }
                        runs++
                        val innerHelper = ChoTenDataHelper(player)
                        for (sub in ef.subEffects) {
                            applySingleEffect(player, innerHelper, sub)
                        }
                    }
                    consumableTaskIds.getOrPut(player.uniqueId.toString()) { mutableListOf() }.add(task)
                }
                "counter_choco" -> {
                    val min = ef.value.toInt().coerceAtLeast(1)
                    val max = ef.amplifier.coerceAtLeast(min)
                    val addValue = if (max > min) (min..max).random() else min
                    val newCounter = helper.data.chocoCounter + addValue
                    helper.data.chocoCounter = newCounter.coerceAtLeast(0)
                    // Death risk: each point over 10 = 5%
                    if (newCounter > 10) {
                        val risk = ((newCounter - 10) * 5).coerceAtMost(100)
                        if (kotlin.random.Random.nextInt(100) < risk) {
                            player.damage(10000.0)
                        }
                    }
                    // Apply timed attribute effects
                    when (ef.statusEffectId) {
                        "classic" -> io.github.zzzyyylllty.chotentech.function.ChocoManager.applyClassic(player)
                        "red" -> io.github.zzzyyylllty.chotentech.function.ChocoManager.applyRed(player)
                        "white" -> io.github.zzzyyylllty.chotentech.function.ChocoManager.applyWhite(player)
                    }
                }
                "health_threshold" -> {
                    val thresholdPercent = ef.value.coerceIn(0.0, 100.0)
                    val targetPercent = (ef.amplifier.toDouble().takeIf { it > 0 } ?: thresholdPercent).coerceIn(0.0, 100.0)
                    if (player.health / player.maxHealth < thresholdPercent / 100.0) {
                        player.health = player.maxHealth * (targetPercent / 100.0)
                    }
                }
                "injury_threshold" -> {
                    val thresholdPercent = ef.value.coerceIn(0.0, 100.0)
                    val targetPercent = (ef.amplifier.toDouble().takeIf { it > 0 } ?: thresholdPercent).coerceIn(0.0, 100.0)
                    if (helper.data.externalInjury / helper.data.maxExternalInjury < thresholdPercent / 100.0) {
                        helper.setExternalInjury(helper.data.maxExternalInjury * (targetPercent / 100.0))
                    }
                    if (helper.data.internalInjury / helper.data.maxInternalInjury < thresholdPercent / 100.0) {
                        helper.setInternalInjury(helper.data.maxInternalInjury * (targetPercent / 100.0))
                    }
                }
            }
        }
    }
}

private val consumableConfigs = ConcurrentHashMap<String, ConsumableConfig>()

fun getConsumableConfig(id: String): ConsumableConfig? = consumableConfigs[id]

fun loadConsumableConfigs() {
    consumableConfigs.clear()
    val dir = File(getDataFolder(), "consumables")
    if (!dir.exists()) dir.mkdirs()
    // 资源中缺失的配置文件复制到数据目录(仅复制不覆盖,保留服务端修改)
    io.github.zzzyyylllty.chotentech.ChoTenTech::class.java.classLoader.getResource("consumables")?.let { res ->
        val jarDir = File(res.path)
        if (jarDir.isDirectory) {
            jarDir.listFiles { f -> f.extension in listOf("yml", "yaml") }?.forEach { f ->
                val target = File(dir, f.name)
                if (!target.exists()) f.copyTo(target)
            }
        }
    }
    val files = dir.listFiles { f -> f.extension in listOf("yml", "yaml") } ?: return
    for (file in files) {
        loadConsumableFile(file)
    }
}

private fun loadConsumableFile(file: File) {
    try {
        val config = Configuration.loadFromFile(file)
        for (key in config.getKeys(false)) {
            val sec = config.getConfigurationSection(key) ?: continue
            val id = sec.getString("id") ?: continue
            val effects = mutableListOf<ConsumableEffect>()

            val effectsList = sec.getList("effects") ?: emptyList<Any>()
            for (raw in effectsList) {
                if (raw !is Map<*, *>) continue
                @Suppress("UNCHECKED_CAST")
                val map = raw as Map<String, Any>
                val effectType = map["effect"]?.toString() ?: continue
                val mode = map["mode"]?.toString() ?: "add"
                val value = (map["value"]?.toString()?.toDoubleOrNull()) ?: 0.0
                val chance = (map["chance"]?.toString()?.toDoubleOrNull()) ?: 100.0

                when (effectType) {
                    "potion" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value,
                        potionId = map["potion"]?.toString(),
                        duration = (map["duration"]?.toString()?.toIntOrNull()) ?: 0,
                        amplifier = (map["amplifier"]?.toString()?.toIntOrNull()) ?: 0,
                        chance = chance,
                    ))
                    "sound" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value,
                        sound = map["sound"]?.toString(),
                        soundVolume = (map["volume"]?.toString()?.toFloatOrNull()) ?: 1.0f,
                        soundPitch = (map["pitch"]?.toString()?.toFloatOrNull()) ?: 1.0f,
                        chance = chance,
                    ))
                    "particle" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value,
                        potionId = map["particle"]?.toString(),
                        duration = (map["count"]?.toString()?.toIntOrNull()) ?: 1,
                        amplifier = (map["speed"]?.toString()?.toIntOrNull()) ?: 0,
                        chance = chance,
                    ))
                    "mm_skill" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode,
                        value = (map["power"]?.toString()?.toDoubleOrNull()) ?: 1.0,
                        sound = map["skill"]?.toString(),
                        chance = chance,
                    ))
                    "tick_task" -> {
                        val delay = (map["delay"]?.toString()?.toIntOrNull()) ?: 0
                        val period = (map["period"]?.toString()?.toIntOrNull()) ?: 20
                        val repeats = (map["repeats"]?.toString()?.toIntOrNull()) ?: 0
                        val rawSubs = map["effects"] as? List<Map<*, *>> ?: emptyList()
                        val subEffects = rawSubs.mapNotNull { sub ->
                            parseSingleEffect(sub)
                        }
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode, value = value,
                            taskDelay = delay, taskPeriod = period, taskRepeats = repeats,
                            subEffects = subEffects,
                            chance = chance,
                        ))
                    }
                    "modifier" -> {
                        val modId = map["modifier"]?.toString()
                        val amount = map["amount"]?.toString()?.toDoubleOrNull() ?: 0.0
                        val type = parseModifierType(map["type"]?.toString())
                        val source = parseModifierSource(map["source"]?.toString())
                        val slot = parseAttributeSlot(map["slot"]?.toString())
                        val modUuid = map["uuid"]?.toString()
                        val expireStr = map["expire"]?.toString()
                        val expireMs = parseDuration(expireStr)
                        val deathMark = map["death_mark"]?.toString()?.toBoolean() ?: false
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode, value = value,
                            modifierId = modId, modifierAmount = amount,
                            modifierType = type, modifierSource = source, modifierSlot = slot,
                            modifierUuid = modUuid, modifierExpire = expireMs,
                            deathMark = deathMark,
                            chance = chance,
                        ))
                    }
                    "status_effect" -> {
                        val seId = map["status_effect"]?.toString()
                        val level = (map["level"]?.toString()?.toIntOrNull()) ?: 0
                        val durationSec = (map["duration"]?.toString()?.toIntOrNull()) ?: 0
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode, value = value,
                            statusEffectId = seId, statusEffectLevel = level,
                            duration = durationSec,
                            chance = chance,
                        ))
                    }
                    "sanity_by_darkness" -> {
                        val thresholds = map["thresholds"]?.toString() ?: ""
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode, value = value,
                            thresholds = thresholds,
                            chance = chance,
                        ))
                    }
                    "counter_choco" -> {
                        val rawValue = map["value"]?.toString() ?: "1"
                        val chocoMin = (map["min"]?.toString()?.toIntOrNull())
                        val chocoMax = (map["max"]?.toString()?.toIntOrNull()) ?: (rawValue.toIntOrNull() ?: 1)
                        val chocoType = map["type"]?.toString() ?: ""
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode,
                            value = (chocoMin ?: chocoMax).toDouble(),
                            amplifier = chocoMax,
                            statusEffectId = chocoType,
                            chance = chance,
                        ))
                    }
                    else -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value,
                        chance = chance,
                        cap = (map["cap"]?.toString()?.toDoubleOrNull()) ?: 0.0,
                    ))
                }
            }

            if (effects.isNotEmpty()) {
                val instant = sec.getBoolean("instant", false)
                val medical = sec.getBoolean("medical", false)
                consumableConfigs[id] = ConsumableConfig(id, effects, instant, medical)
            }
        }
    } catch (e: Exception) {
        taboolib.common.platform.function.warning("Failed to load consumable file ${file.name}: ${e.message}")
    }
}

private fun parseSingleEffect(map: Map<*, *>): ConsumableEffect? {
    val type = map["effect"]?.toString() ?: return null
    val mode = map["mode"]?.toString() ?: "add"
    val value = (map["value"]?.toString()?.toDoubleOrNull()) ?: 0.0
    val chance = (map["chance"]?.toString()?.toDoubleOrNull()) ?: 100.0
    return when (type) {
        "health" -> ConsumableEffect(
            effect = type, mode = mode, value = value, chance = chance,
            cap = (map["cap"]?.toString()?.toDoubleOrNull()) ?: 0.0,
        )
        "hunger", "saturation", "consumption", "sanity", "darkness",
        "stress", "stamina", "hand_stamina", "handstamina",
        "external_injury", "externalinjury", "internal_injury", "internalinjury" ->
            ConsumableEffect(effect = type, mode = mode, value = value, chance = chance)
        "status_effect" -> ConsumableEffect(
            effect = type, mode = mode, value = value,
            statusEffectId = map["status_effect"]?.toString(),
            statusEffectLevel = (map["level"]?.toString()?.toIntOrNull()) ?: 0,
            duration = (map["duration"]?.toString()?.toIntOrNull()) ?: 0,
            chance = chance,
        )
        "sanity_by_darkness" -> ConsumableEffect(
            effect = type, mode = mode, value = value,
            thresholds = map["thresholds"]?.toString() ?: "",
            chance = chance,
        )
        "potion" -> ConsumableEffect(
            effect = type, mode = mode, value = value,
            potionId = map["potion"]?.toString(),
            duration = (map["duration"]?.toString()?.toIntOrNull()) ?: 0,
            amplifier = (map["amplifier"]?.toString()?.toIntOrNull()) ?: 0,
            chance = chance,
        )
        "counter_choco" -> {
            val rawValue = map["value"]?.toString() ?: "1"
            val chocoMin = (map["min"]?.toString()?.toIntOrNull())
            val chocoMax = (map["max"]?.toString()?.toIntOrNull()) ?: (rawValue.toIntOrNull() ?: 1)
            val chocoType = map["type"]?.toString() ?: ""
            ConsumableEffect(
                effect = type, mode = mode,
                value = (chocoMin ?: chocoMax).toDouble(),
                amplifier = chocoMax,
                statusEffectId = chocoType,
                chance = chance,
            )
        }
        else -> ConsumableEffect(effect = type, mode = mode, value = value, chance = chance)
    }
}

private fun parseModifierType(s: String?): ModifierType = when (s?.uppercase()) {
    "ADD" -> ModifierType.ADD
    "PERCENT", "MULTIPLY" -> ModifierType.PERCENT
    else -> ModifierType.ADD
}

private fun parseModifierSource(s: String?): ModifierSource = when (s?.uppercase()) {
    "HAND" -> ModifierSource.HAND
    "MAINHAND", "MAIN_HAND" -> ModifierSource.MAINHAND
    "OFFHAND", "OFF_HAND" -> ModifierSource.OFFHAND
    "MELEE" -> ModifierSource.MELEE
    "RANGED" -> ModifierSource.RANGED
    "ARMOR" -> ModifierSource.ARMOR
    "SKILL" -> ModifierSource.SKILL
    "POTION" -> ModifierSource.POTION
    "HEALING" -> ModifierSource.HEALING
    else -> ModifierSource.OTHER
}

private fun parseAttributeSlot(s: String?): AttributeSlot = when (s?.uppercase()) {
    "HEAD" -> AttributeSlot.HEAD
    "CHEST", "CHESTPLATE" -> AttributeSlot.CHEST
    "LEGS", "LEGGINGS" -> AttributeSlot.LEGS
    "FEET", "BOOTS" -> AttributeSlot.FEET
    "INVENTORY" -> AttributeSlot.INVENTORY
    "MAINHAND", "MAIN_HAND" -> AttributeSlot.MAINHAND
    "OFFHAND", "OFF_HAND" -> AttributeSlot.OFFHAND
    else -> AttributeSlot.OTHER
}

/** Parse duration strings like "10m", "30s", "1h", "2d" into milliseconds. null = permanent. */
private fun parseDuration(s: String?): Long? {
    if (s == null) return null
    val trimmed = s.trim().lowercase()
    if (trimmed == "permanent" || trimmed == "perm" || trimmed == "inf" || trimmed == "-1") return null
    val regex = Regex("^(\\d+)\\s*([smhd])$")
    val match = regex.find(trimmed) ?: return null
    val amount = match.groupValues[1].toLongOrNull() ?: return null
    return when (match.groupValues[2]) {
        "s" -> amount * 1000L
        "m" -> amount * 60_000L
        "h" -> amount * 3_600_000L
        "d" -> amount * 86_400_000L
        else -> null
    }
}

/**
 * Parse sanity_by_darkness thresholds string.
 * Format: "max:value;max:value;...;:default"
 * Example: "19:12;39:4;59:2;:1" means:
 *   darkness≤19→+12, 20-39→+4, 40-59→+2, 60+→+1
 */
private fun parseSanityByDarknessThresholds(thresholds: String, darkness: Double): Double? {
    val segments = thresholds.split(";")
    var prevMax = -1.0
    for (segment in segments) {
        val parts = segment.split(":")
        if (parts.size != 2) continue
        val value = parts[1].toDoubleOrNull() ?: continue
        val max = if (parts[0].isBlank()) Double.MAX_VALUE else parts[0].toDoubleOrNull() ?: continue
        if (darkness <= max && darkness > prevMax) return value
        prevMax = max
    }
    return null
}
