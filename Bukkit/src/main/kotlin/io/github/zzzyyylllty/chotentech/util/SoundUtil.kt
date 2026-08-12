package io.github.zzzyyylllty.chotentech.util

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.Location
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import kotlin.math.sqrt

object SoundUtil {
    fun playSound(player: Player, soundName: String, source: Sound.Source, volume: Float, pitch: Float) {
        submit { player.playSound(Sound.sound(
            Key.key(soundName),
            source,
            volume,
            pitch
        ))
        }
    }

    /**
     * 带最小音量播放声音，对应原版 /playsound 指令的 minVolume 参数：
     * 玩家在 volume*16 格范围内 → 正常音量播放；
     * 超出范围 → 在玩家附近 2 格处以 minVolume 播放（保证仍可闻）；
     * 超出范围且 minVolume <= 0 → 不播放，返回 false
     */
    fun playSoundWithMinVolume(
        player: Player,
        position: Location,
        soundKey: String,
        source: SoundCategory,
        volume: Float,
        minVolume: Float,
        pitch: Float
    ): Boolean {
        val dx = position.x - player.x
        val dy = position.y - player.y
        val dz = position.z - player.z
        val distSqr = dx * dx + dy * dy + dz * dz
        val maxDist = (volume * 16.0).toInt()
        val maxDistSqr = (maxDist * maxDist).toDouble()

        var localVolume = volume
        var localPosition = position
        if (distSqr > maxDistSqr) {
            if (minVolume <= 0f) return false
            val distance = sqrt(distSqr)
            localPosition = position.clone().apply {
                x = player.x + dx / distance * 2.0
                y = player.y + dy / distance * 2.0
                z = player.z + dz / distance * 2.0
            }
            localVolume = minVolume
        }
        player.playSound(localPosition, soundKey, source, localVolume, pitch)
        return true
    }

    fun playConfiguredSound(player: Player, soundName: String) {
        val split = config.getString("sounds.$soundName")?.split(" ") ?: return
        val sound = split[0]
        val volume = if (split.size >= 2) split[1].toFloat() else 1.0F
        val pitch = if (split.size >= 3) split[2].toFloat() else 1.0F
        val source = if (split.size >= 4) Sound.Source.valueOf(split[3].uppercase()) else Sound.Source.MASTER
        playSound(player, sound, source, volume, pitch)
    }
}