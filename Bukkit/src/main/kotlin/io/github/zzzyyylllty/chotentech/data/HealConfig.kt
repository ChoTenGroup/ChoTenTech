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
    val sData = this["sertraline_data"]?.asCompound()

    val durability = sData?.get("durability")?.let {
        it.asDouble() ?: it.asString()?.toDoubleOrNull()
    } ?: return null
    val maxDurability = sData["maxdurability"]?.let {
        it.asDouble() ?: it.asString()?.toDoubleOrNull()
    } ?: durability

    fun d(key: String, default: Double = 0.0): Double =
        heal[key]?.asDouble() ?: default

    val timeCompound = heal["time"]?.asCompound()
    val preTime = timeCompound?.get("pre")?.let { it.asDouble() ?: it.asString()?.toDoubleOrNull() } ?: 2.0
    val perTime = timeCompound?.get("per")?.let { it.asDouble() ?: it.asString()?.toDoubleOrNull() } ?: 1.0
    val postTime = timeCompound?.get("post")?.let { it.asDouble() ?: it.asString()?.toDoubleOrNull() } ?: 2.0

    val cancelHeal: Set<String> = heal["cancelheal"]?.asList()?.mapNotNull {
        it?.asString()?.lowercase()
    }?.toSet() ?: DEFAULT_CANCEL_CONDITIONS

    val costCompound = heal["cost"]?.asCompound()
    val costFood = costCompound?.get("food")?.let { it.asDouble() ?: it.asString()?.toDoubleOrNull() } ?: 0.0
    val costSan = costCompound?.get("san")?.let { it.asDouble() ?: it.asString()?.toDoubleOrNull() } ?: 0.0

    val soundCompound = heal["sound"]?.asCompound()

    return HealConfig(
        durability = durability,
        maxDurability = maxDurability,
        preTime = maxOf(preTime, 0.0),
        perTime = maxOf(perTime, 1.0),
        postTime = maxOf(postTime, 0.0),
        maxHeal = d("maxheal"),
        healSpeed = d("healspeed"),
        healRate = d("healrate", 1.0),
        iinjurySpeed = d("iinjuryspeed"),
        iinjuryRate = d("iinjuryrate", 1.0),
        einjurySpeed = d("einjuryspeed"),
        einjuryRate = d("einjuryrate", 1.0),
        maxIInjury = d("maxiinjury"),
        maxEInjury = d("maxeinjury"),
        costFood = costFood,
        costSan = costSan,
        slow = d("slow", 50.0),
        cancelHeal = cancelHeal,
        soundPre = parseSoundConfig(soundCompound?.get("pre")) ?: HealSound.DEFAULT_PRE,
        soundStart = parseSoundConfig(soundCompound?.get("start")) ?: HealSound.DEFAULT_START,
        soundHeal = parseSoundConfig(soundCompound?.get("heal")) ?: HealSound.DEFAULT_HEAL,
        soundComplete = parseSoundConfig(soundCompound?.get("complete")) ?: HealSound.DEFAULT_COMPLETE,
        soundCancel = parseSoundConfig(soundCompound?.get("cancel")) ?: HealSound.DEFAULT_CANCEL,
        soundExhaust = parseSoundConfig(soundCompound?.get("exhaust")) ?: HealSound.DEFAULT_EXHAUST,
        soundEnd = parseSoundConfig(soundCompound?.get("end")) ?: HealSound.DEFAULT_END,
        soundAlreadyFull = parseSoundConfig(soundCompound?.get("alreadyfull")) ?: HealSound.DEFAULT_ALREADY_FULL,
        soundCapReached = parseSoundConfig(soundCompound?.get("capreached")) ?: HealSound.DEFAULT_CAP_REACHED
    )
}
