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
    val thresholds: String = ""
)

/** Track active tick_task PlatformTask references per player UUID. Cancelled on death. */
val consumableTaskIds = ConcurrentHashMap<String, MutableList<PlatformExecutor.PlatformTask>>()

fun cancelConsumableTasks(playerId: String) {
    consumableTaskIds.remove(playerId)?.forEach { task ->
        task.cancel()
    }
}

data class ConsumableConfig(
    val id: String,
    val effects: List<ConsumableEffect>
) {
    fun apply(player: Player) {
        val helper = ChoTenDataHelper(player)
        for (ef in effects) {
            applySingleEffect(player, helper, ef)
        }
    }

    companion object {
        fun applySingleEffect(player: Player, helper: ChoTenDataHelper, ef: ConsumableEffect) {
            when (ef.effect) {
                "health" -> {
                    val hp = player.health
                    val maxHp = player.maxHealth
                    when (ef.mode) {
                        "add" -> player.health = minOf(hp + ef.value, maxHp)
                        "set" -> player.health = ef.value.coerceIn(0.0, maxHp)
                        "percent" -> player.health = minOf(hp + maxHp * (ef.value / 100.0), maxHp)
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
                        expireOn = expireOn
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
            }
        }
    }
}

private val consumableConfigs = ConcurrentHashMap<String, ConsumableConfig>()

fun getConsumableConfig(id: String): ConsumableConfig? = consumableConfigs[id]

fun loadConsumableConfigs() {
    consumableConfigs.clear()
    val dir = File(getDataFolder(), "consumables")
    if (!dir.exists()) {
        dir.mkdirs()
        io.github.zzzyyylllty.chotentech.ChoTenTech::class.java.classLoader.getResource("consumables")?.let { res ->
            val jarDir = File(res.path)
            if (jarDir.isDirectory) {
                jarDir.listFiles { f -> f.extension in listOf("yml", "yaml") }?.forEach { f ->
                    val target = File(dir, f.name)
                    if (!target.exists()) f.copyTo(target)
                }
            }
        }
        return
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

                when (effectType) {
                    "potion" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value,
                        potionId = map["potion"]?.toString(),
                        duration = (map["duration"]?.toString()?.toIntOrNull()) ?: 0,
                        amplifier = (map["amplifier"]?.toString()?.toIntOrNull()) ?: 0
                    ))
                    "sound" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value,
                        sound = map["sound"]?.toString(),
                        soundVolume = (map["volume"]?.toString()?.toFloatOrNull()) ?: 1.0f,
                        soundPitch = (map["pitch"]?.toString()?.toFloatOrNull()) ?: 1.0f
                    ))
                    "particle" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value,
                        potionId = map["particle"]?.toString(),
                        duration = (map["count"]?.toString()?.toIntOrNull()) ?: 1,
                        amplifier = (map["speed"]?.toString()?.toIntOrNull()) ?: 0
                    ))
                    "mm_skill" -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode,
                        value = (map["power"]?.toString()?.toDoubleOrNull()) ?: 1.0,
                        sound = map["skill"]?.toString()
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
                            subEffects = subEffects
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
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode, value = value,
                            modifierId = modId, modifierAmount = amount,
                            modifierType = type, modifierSource = source, modifierSlot = slot,
                            modifierUuid = modUuid, modifierExpire = expireMs
                        ))
                    }
                    "status_effect" -> {
                        val seId = map["status_effect"]?.toString()
                        val level = (map["level"]?.toString()?.toIntOrNull()) ?: 0
                        val durationSec = (map["duration"]?.toString()?.toIntOrNull()) ?: 0
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode, value = value,
                            statusEffectId = seId, statusEffectLevel = level,
                            duration = durationSec
                        ))
                    }
                    "sanity_by_darkness" -> {
                        val thresholds = map["thresholds"]?.toString() ?: ""
                        effects.add(ConsumableEffect(
                            effect = effectType, mode = mode, value = value,
                            thresholds = thresholds
                        ))
                    }
                    else -> effects.add(ConsumableEffect(
                        effect = effectType, mode = mode, value = value
                    ))
                }
            }

            if (effects.isNotEmpty()) {
                consumableConfigs[id] = ConsumableConfig(id, effects)
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
    return when (type) {
        "health", "hunger", "saturation", "consumption", "sanity", "darkness",
        "stress", "stamina", "hand_stamina", "handstamina",
        "external_injury", "externalinjury", "internal_injury", "internalinjury" ->
            ConsumableEffect(effect = type, mode = mode, value = value)
        "status_effect" -> ConsumableEffect(
            effect = type, mode = mode, value = value,
            statusEffectId = map["status_effect"]?.toString(),
            statusEffectLevel = (map["level"]?.toString()?.toIntOrNull()) ?: 0,
            duration = (map["duration"]?.toString()?.toIntOrNull()) ?: 0
        )
        "sanity_by_darkness" -> ConsumableEffect(
            effect = type, mode = mode, value = value,
            thresholds = map["thresholds"]?.toString() ?: ""
        )
        "potion" -> ConsumableEffect(
            effect = type, mode = mode, value = value,
            potionId = map["potion"]?.toString(),
            duration = (map["duration"]?.toString()?.toIntOrNull()) ?: 0,
            amplifier = (map["amplifier"]?.toString()?.toIntOrNull()) ?: 0
        )
        else -> ConsumableEffect(effect = type, mode = mode, value = value)
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
