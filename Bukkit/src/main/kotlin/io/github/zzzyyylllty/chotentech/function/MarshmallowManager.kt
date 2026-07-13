package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.Player
import java.util.UUID

object MarshmallowManager {

    private val SPEED_UUID = UUID.nameUUIDFromBytes("marshmallow-speed".toByteArray())
    private val STAMINA_UUID = UUID.nameUUIDFromBytes("marshmallow-stamina".toByteArray())
    private val allUuids = setOf(SPEED_UUID, STAMINA_UUID)

    fun updateEffects(player: Player, counter: Int) {
        when {
            counter in 51..90 -> {
                setModifier(player, SPEED_UUID, "movement_speed", -0.1)
                setModifier(player, STAMINA_UUID, "stamina_regen_multiplier", -0.3)
            }
            counter in 91..120 -> {
                setModifier(player, SPEED_UUID, "movement_speed", -0.2)
                setModifier(player, STAMINA_UUID, "stamina_regen_multiplier", -0.6)
            }
            else -> removeModifiers(player)
        }
    }

    fun removeModifiers(player: Player) {
        val data = AttributeManager.getPlayerPersistentData(player.uniqueId)
        val mods = data.modifiers.toMutableList()
        if (mods.removeAll { it.uuid in allUuids }) {
            data.modifiers = mods
            AttributeManager.savePlayerData(data)
            AttributeManager.updatePlayer(player, async = true)
        }
    }

    private fun setModifier(player: Player, uuid: UUID, attributeId: String, value: Double) {
        val data = AttributeManager.getPlayerPersistentData(player.uniqueId)
        val mods = data.modifiers.toMutableList()
        mods.removeAll { it.uuid == uuid }
        mods.add(AttributeModifier(
            uuid = uuid,
            attributeId = attributeId,
            value = value,
            type = ModifierType.PERCENT,
            source = ModifierSource.OTHER
        ))
        data.modifiers = mods
        AttributeManager.savePlayerData(data)
        AttributeManager.updatePlayer(player, async = true)
    }
}
