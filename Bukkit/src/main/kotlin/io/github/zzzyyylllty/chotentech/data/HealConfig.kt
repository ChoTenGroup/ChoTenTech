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
    val preTime: Double,
    val perTime: Double,
    val postTime: Double,
    val maxHeal: Double,
    val healSpeed: Double,
    val healRate: Double,
    val costFood: Double,
    val costSan: Double,
    val hpLeast: Double = -1.0,
    val removeDebuff: Map<String, Double> = emptyMap(),
    val slow: Double = 50.0,
    val cancelHeal: Set<String> = DEFAULT_CANCEL_CONDITIONS,
    val soundPre: HealSound = HealSound.DEFAULT_PRE,
    val soundStart: HealSound = HealSound.DEFAULT_START,
    val soundHeal: HealSound = HealSound.DEFAULT_HEAL,
    val soundComplete: HealSound = HealSound.DEFAULT_COMPLETE,
    val soundCancel: HealSound = HealSound.DEFAULT_CANCEL,
    val soundExhaust: HealSound = HealSound.DEFAULT_EXHAUST,
    val soundEnd: HealSound = HealSound.DEFAULT_END,
    val soundAlreadyFull: HealSound = HealSound.DEFAULT_ALREADY_FULL,
    val soundCapReached: HealSound = HealSound.DEFAULT_CAP_REACHED
)

val DEFAULT_CANCEL_CONDITIONS = setOf("walk", "sprint", "jump", "attack", "fall")
private val medkitConfigs = ConcurrentHashMap<String, HealConfig>()

fun loadMedkitConfig() {
    medkitConfigs.clear()
    val file = File(getDataFolder(), "medkit/medkit.yml")
    if (!file.exists()) return
    val config = Configuration.loadFromFile(file)
    for (key in config.getKeys(false)) {
        val sec = config.getConfigurationSection(key) ?: continue
        val removals = sec.getConfigurationSection("removedebuff")
        medkitConfigs[key] = HealConfig(
            durability = 0.0, maxDurability = 0.0,
            preTime = sec.getDouble("time.pre", 2.0).coerceAtLeast(0.0),
            perTime = sec.getDouble("time.per", 1.0).coerceAtLeast(.05),
            postTime = sec.getDouble("time.post", 2.0).coerceAtLeast(0.0),
            maxHeal = sec.getDouble("maxheal", 0.0).coerceAtLeast(0.0),
            healSpeed = sec.getDouble("healspeed", 0.0).coerceAtLeast(0.0),
            healRate = sec.getDouble("healrate", 1.0).coerceAtLeast(0.0),
            costFood = sec.getDouble("cost.food", 0.0).coerceAtLeast(0.0),
            costSan = sec.getDouble("cost.san", 0.0).coerceAtLeast(0.0),
            hpLeast = sec.getDouble("hpleast", -1.0),
            removeDebuff = removals?.getKeys(false)?.associateWith { removals.getDouble(it).coerceAtLeast(0.0) } ?: emptyMap()
        )
    }
}

fun getHealConfigFromYaml(sertralineId: String, durability: Double, maxDurability: Double): HealConfig? =
    medkitConfigs[sertralineId]?.copy(durability = durability, maxDurability = maxDurability)
