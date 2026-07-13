package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.data.AttributeModifier
import io.github.zzzyyylllty.chotentech.data.ModifierSource
import io.github.zzzyyylllty.chotentech.data.ModifierType
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import org.bukkit.entity.Player
import java.util.UUID

object ChocoManager {

    private val CLASSIC_UUID = UUID.nameUUIDFromBytes("choco-classic".toByteArray())
    private val RED_A_UUID = UUID.nameUUIDFromBytes("choco-red-a".toByteArray())
    private val RED_B_UUID = UUID.nameUUIDFromBytes("choco-red-b".toByteArray())
    private val WHITE_UUID = UUID.nameUUIDFromBytes("choco-white".toByteArray())

    private val allUuids = setOf(CLASSIC_UUID, RED_A_UUID, RED_B_UUID, WHITE_UUID)

    fun applyClassic(player: Player) {
        val expiry = System.currentTimeMillis() + 60_000L
        replaceModifiers(
            player, CLASSIC_UUID, listOf(
                AttributeModifier(CLASSIC_UUID, "stamina_regen_multiplier", 1.0, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
                AttributeModifier(CLASSIC_UUID, "movement_speed", 0.2, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
                AttributeModifier(CLASSIC_UUID, "attack_damage", 0.2, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
                AttributeModifier(CLASSIC_UUID, "mining_power", 1.0, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
            )
        )
    }

    fun applyRed(player: Player) {
        val expiryA = System.currentTimeMillis() + 60_000L
        val expiryB = System.currentTimeMillis() + 300_000L
        replaceModifiers(
            player, RED_A_UUID, listOf(
                AttributeModifier(RED_A_UUID, "stamina_regen_multiplier", 1.0, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryA),
                AttributeModifier(RED_A_UUID, "movement_speed", 0.2, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryA),
                AttributeModifier(RED_A_UUID, "attack_damage", 0.8, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryA),
                AttributeModifier(RED_A_UUID, "mining_power", 2.0, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryA),
            )
        )
        replaceModifiers(
            player, RED_B_UUID, listOf(
                AttributeModifier(RED_B_UUID, "stamina_regen_multiplier", 1.0, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryB),
                AttributeModifier(RED_B_UUID, "movement_speed", 0.2, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryB),
                AttributeModifier(RED_B_UUID, "attack_damage", 0.2, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryB),
                AttributeModifier(RED_B_UUID, "mining_power", 1.0, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiryB),
            )
        )
    }

    fun applyWhite(player: Player) {
        val expiry = System.currentTimeMillis() + 120_000L
        replaceModifiers(
            player, WHITE_UUID, listOf(
                AttributeModifier(WHITE_UUID, "stamina_regen_multiplier", 0.7, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
                AttributeModifier(WHITE_UUID, "movement_speed", 0.2, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
                AttributeModifier(WHITE_UUID, "attack_damage", 0.1, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
                AttributeModifier(WHITE_UUID, "mining_power", 0.5, ModifierType.PERCENT, ModifierSource.OTHER, expireOn = expiry),
            )
        )
    }

    fun removeAll(player: Player) {
        val data = AttributeManager.getPlayerPersistentData(player.uniqueId)
        val mods = data.modifiers.toMutableList()
        if (mods.removeAll { it.uuid in allUuids }) {
            data.modifiers = mods
            AttributeManager.savePlayerData(data)
            AttributeManager.updatePlayer(player, async = true)
        }
    }

    private fun replaceModifiers(player: Player, groupUuid: UUID, newMods: List<AttributeModifier>) {
        val data = AttributeManager.getPlayerPersistentData(player.uniqueId)
        val mods = data.modifiers.toMutableList()
        mods.removeAll { it.uuid == groupUuid }
        mods.addAll(newMods)
        data.modifiers = mods
        AttributeManager.savePlayerData(data)
        AttributeManager.updatePlayer(player, async = true)
    }
}
