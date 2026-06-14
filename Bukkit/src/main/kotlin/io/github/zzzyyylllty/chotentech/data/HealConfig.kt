package io.github.zzzyyylllty.chotentech.data

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import taboolib.common.platform.function.getDataFolder
import taboolib.module.configuration.Configuration
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class HealSound(
    val key: String,
    val source: Sound.Source = Sound.Source.MASTER,
    val volume: Float = 1.0f,
    val pitch: Float = 1.0f
) {
    fun play(player: org.bukkit.entity.Player) {
        player.playSound(Sound.sound(Key.key(key), source, volume, pitch))
    }

    companion object {
        val DEFAULT_PRE = HealSound("minecraft:item.armor.equip_leather", volume = 0.3f, pitch = 0.6f)
        val DEFAULT_START = HealSound("minecraft:entity.player.levelup", volume = 0.5f, pitch = 1.2f)
        val DEFAULT_HEAL = HealSound("minecraft:item.armor.equip_elytra", volume = 0.3f, pitch = 1.4f)
        val DEFAULT_COMPLETE = HealSound("minecraft:block.note_block.bell", volume = 0.4f, pitch = 0.8f)
        val DEFAULT_CANCEL = HealSound("minecraft:block.note_block.bass", volume = 0.3f, pitch = 0.5f)
        val DEFAULT_EXHAUST = HealSound("minecraft:block.note_block.iron_xylophone", volume = 0.3f, pitch = 0.4f)
        val DEFAULT_END = HealSound("minecraft:item.armor.equip_chain", volume = 0.4f, pitch = 0.8f)
        val DEFAULT_ALREADY_FULL = HealSound("minecraft:block.note_block.guitar", volume = 0.3f, pitch = 0.5f)
        val DEFAULT_CAP_REACHED = HealSound("minecraft:entity.experience_orb.pickup", volume = 0.3f, pitch = 0.6f)
    }
}

data class HealConfig(
    val durability: Double,
    val maxDurability: Double,
    val preTime: Double,         // seconds before first heal, default 2
    val perTime: Double,         // seconds between heals, default 1
    val postTime: Double,        // seconds of endlag, default 2
    val maxHeal: Double,
    val healSpeed: Double,
    val healRate: Double,
    val iinjurySpeed: Double,
    val iinjuryRate: Double,
    val einjurySpeed: Double,
    val einjuryRate: Double,
    val maxIInjury: Double,
    val maxEInjury: Double,
    val costFood: Double,
    val costSan: Double,
    val slow: Double,
    val cancelHeal: Set<String>,
    val soundPre: HealSound,
    val soundStart: HealSound,
    val soundHeal: HealSound,
    val soundComplete: HealSound,
    val soundCancel: HealSound,
    val soundExhaust: HealSound,
    val soundEnd: HealSound,
    val soundAlreadyFull: HealSound,
    val soundCapReached: HealSound
)

val DEFAULT_CANCEL_CONDITIONS = setOf("walk", "sprint", "jump", "attack", "fall")

// ==================== Medkit YAML Config ====================

private val medkitConfigs = ConcurrentHashMap<String, MedkitYamlConfig>()

data class MedkitYamlConfig(
    val maxheal: Double,
    val healspeed: Double,
    val healrate: Double,
    val iinjuryspeed: Double,
    val einjuryspeed: Double,
    val iinjuryrate: Double,
    val einjuryrate: Double,
    val maxiinjury: Double,
    val maxeinjury: Double,
    val preTime: Double,
    val perTime: Double,
    val postTime: Double,
    val costFood: Double,
    val costSan: Double
)

fun loadMedkitConfig() {
    medkitConfigs.clear()
    val file = File(getDataFolder(), "medkit/medkit.yml")
    if (!file.exists()) return
    val config = Configuration.loadFromFile(file)
    for (key in config.getKeys(false)) {
        try {
            val sec = config.getConfigurationSection(key) ?: continue
            val time = sec.getConfigurationSection("time")
            val cost = sec.getConfigurationSection("cost")
            medkitConfigs[key] = MedkitYamlConfig(
                maxheal = sec.getDouble("maxheal", 0.0),
                healspeed = sec.getDouble("healspeed", 0.0),
                healrate = sec.getDouble("healrate", 1.0),
                iinjuryspeed = sec.getDouble("iinjuryspeed", 0.0),
                einjuryspeed = sec.getDouble("einjuryspeed", 0.0),
                iinjuryrate = sec.getDouble("iinjuryrate", 1.0),
                einjuryrate = sec.getDouble("einjuryrate", 1.0),
                maxiinjury = sec.getDouble("maxiinjury", 0.0),
                maxeinjury = sec.getDouble("maxeinjury", 0.0),
                preTime = time?.getDouble("pre") ?: 2.0,
                perTime = time?.getDouble("per") ?: 1.0,
                postTime = time?.getDouble("post") ?: 2.0,
                costFood = cost?.getDouble("food") ?: 0.0,
                costSan = cost?.getDouble("san") ?: 0.0
            )
        } catch (_: Exception) { /* skip */ }
    }
}

fun getHealConfigFromYaml(sertralineId: String, durability: Double, maxDurability: Double): HealConfig? {
    val yaml = medkitConfigs[sertralineId] ?: return null
    return HealConfig(
        durability = durability,
        maxDurability = maxDurability,
        preTime = maxOf(yaml.preTime, 0.0),
        perTime = maxOf(yaml.perTime, 1.0),
        postTime = maxOf(yaml.postTime, 0.0),
        maxHeal = yaml.maxheal,
        healSpeed = yaml.healspeed,
        healRate = yaml.healrate,
        iinjurySpeed = yaml.iinjuryspeed,
        iinjuryRate = yaml.iinjuryrate,
        einjurySpeed = yaml.einjuryspeed,
        einjuryRate = yaml.einjuryrate,
        maxIInjury = yaml.maxiinjury,
        maxEInjury = yaml.maxeinjury,
        costFood = yaml.costFood,
        costSan = yaml.costSan,
        slow = 50.0,
        cancelHeal = DEFAULT_CANCEL_CONDITIONS,
        soundPre = HealSound.DEFAULT_PRE,
        soundStart = HealSound.DEFAULT_START,
        soundHeal = HealSound.DEFAULT_HEAL,
        soundComplete = HealSound.DEFAULT_COMPLETE,
        soundCancel = HealSound.DEFAULT_CANCEL,
        soundExhaust = HealSound.DEFAULT_EXHAUST,
        soundEnd = HealSound.DEFAULT_END,
        soundAlreadyFull = HealSound.DEFAULT_ALREADY_FULL,
        soundCapReached = HealSound.DEFAULT_CAP_REACHED
    )
}
