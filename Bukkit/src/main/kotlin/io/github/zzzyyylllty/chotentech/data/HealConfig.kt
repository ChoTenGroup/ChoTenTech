package io.github.zzzyyylllty.chotentech.data

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData

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
        val DEFAULT_START = HealSound("minecraft:entity.player.levelup", volume = 0.5f, pitch = 1.2f)
        val DEFAULT_HEAL = HealSound("minecraft:block.note_block.pling", volume = 0.3f, pitch = 1.4f)
        val DEFAULT_END = HealSound("minecraft:block.note_block.chime", volume = 0.4f, pitch = 0.8f)
    }
}

data class HealConfig(
    val durability: Double,
    val maxDurability: Double,   // for display only, defaults to durability
    val maxHeal: Double,         // -1 = infinite, 0/null = can't heal HP
    val healSpeed: Double,       // -1 = instant, 0/null = can't heal HP
    val healRate: Double,        // default 1
    val iinjurySpeed: Double,    // 0/null = can't heal internal injury
    val iinjuryRate: Double,     // default 1
    val einjurySpeed: Double,    // 0/null = can't heal external injury
    val einjuryRate: Double,     // default 1
    val maxIInjury: Double,      // 0/null = can't heal internal injury
    val maxEInjury: Double,      // 0/null = can't heal external injury
    val slow: Double,            // speed reduction percentage (0-100)
    val cancelHeal: Set<String>, // walk, sprint, jump, attack, fall
    val soundStart: HealSound,
    val soundHeal: HealSound,
    val soundEnd: HealSound
)

val DEFAULT_CANCEL_CONDITIONS = setOf("walk", "sprint", "jump", "attack", "fall")

private fun parseSoundConfig(data: ItemTagData?): HealSound? {
    if (data == null) return null
    // Simple string form: "minecraft:block.note_block.pling"
    data.asString()?.let { return HealSound(key = it) }
    // Compound form: {key: "...", volume: 1.0, pitch: 1.0, source: "MASTER"}
    val compound = data.asCompound() ?: return null
    val key = compound["key"]?.asString() ?: return null
    val volume = compound["volume"]?.asDouble()?.toFloat() ?: 1.0f
    val pitch = compound["pitch"]?.asDouble()?.toFloat() ?: 1.0f
    val source = compound["source"]?.asString()?.let {
        try { Sound.Source.valueOf(it.uppercase()) } catch (_: Exception) { Sound.Source.MASTER }
    } ?: Sound.Source.MASTER
    return HealSound(key = key, source = source, volume = volume, pitch = pitch)
}

fun ItemTag.parseHealConfig(): HealConfig? {
    val heal = this["heal"]?.asCompound() ?: return null

    val durability = heal["durability"]?.asDouble() ?: return null
    val maxDurability = heal["maxdurability"]?.asDouble() ?: durability

    fun d(key: String, default: Double = 0.0): Double =
        heal[key]?.asDouble() ?: default

    val cancelHeal: Set<String> = heal["cancelheal"]?.asList()?.mapNotNull {
        it?.asString()?.lowercase()
    }?.toSet() ?: DEFAULT_CANCEL_CONDITIONS

    val soundCompound = heal["sound"]?.asCompound()

    return HealConfig(
        durability = durability,
        maxDurability = maxDurability,
        maxHeal = d("maxheal"),
        healSpeed = d("healspeed"),
        healRate = d("healrate", 1.0),
        iinjurySpeed = d("iinjuryspeed"),
        iinjuryRate = d("iinjuryrate", 1.0),
        einjurySpeed = d("einjuryspeed"),
        einjuryRate = d("einjuryrate", 1.0),
        maxIInjury = d("maxiinjury"),
        maxEInjury = d("maxeinjury"),
        slow = d("slow"),
        cancelHeal = cancelHeal,
        soundStart = parseSoundConfig(soundCompound?.get("start")) ?: HealSound.DEFAULT_START,
        soundHeal = parseSoundConfig(soundCompound?.get("heal")) ?: HealSound.DEFAULT_HEAL,
        soundEnd = parseSoundConfig(soundCompound?.get("end")) ?: HealSound.DEFAULT_END
    )
}

fun ItemTagData?.isHealItem(): Boolean {
    return this?.asCompound()?.containsKey("heal") == true
}
