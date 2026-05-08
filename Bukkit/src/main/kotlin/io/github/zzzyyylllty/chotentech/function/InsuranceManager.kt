package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.InsuredItem
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.EconomyUtil
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmStrictUtil
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common5.cByteArray
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.getItemTag
import java.time.Instant
import java.util.UUID

object InsuranceManager {

    private val expireDays by lazy { config.getLong("insurance.expire-days", 7) }

    private val TOOL_AND_ARMOR_SUFFIXES = setOf(
        "_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE",
        "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"
    )

    fun isInsurable(item: ItemStack): Boolean {
        if (item.type.isAir) return false
        val name = item.type.name.uppercase()
        devLog("insurance: $name")
        val isInsurable = TOOL_AND_ARMOR_SUFFIXES.any { name.endsWith(it) }
        return isInsurable
    }

    fun isAlreadyInsured(item: ItemStack): Boolean {
        val tag = item.getItemTag()
        return tag.getDeep("choten_insurance")?.asString() != null
    }

    fun getInsuredOwner(item: ItemStack): String? {
        val tag = item.getItemTag()
        return tag.getDeep("choten_insurance")?.asString()
    }

    private fun getItemTier(item: ItemStack): String {
        val tag = item.getItemTag()
        return tag.getDeep("sertraline_tier")?.asString()?.uppercase() ?: "D"
    }

    fun getTierCost(tier: String): Double {
        val section = config.getConfigurationSection("insurance.tiers") ?: return 100.0
        return section.getDouble(tier.uppercase(), section.getDouble("D", 100.0))
    }

    fun getInsuranceCost(item: ItemStack): Double {
        return getTierCost(getItemTier(item))
    }

    fun tagItem(item: ItemStack, player: Player): ItemStack {
        val tag = item.getItemTag()
        tag.putDeep("choten_insurance", ItemTagData(player.name))
        tag.saveTo(item)

        val meta = item.itemMeta ?: return item
        val existingName = meta.displayName()
        val suffix = mmUtil.deserialize(" <gold>[已投保]")
        meta.displayName(
            if (existingName != null) existingName.append(suffix)
            else Component.translatable(item.type.translationKey()).append(suffix)
        )
        item.itemMeta = meta
        return item
    }

    fun untagItem(item: ItemStack): ItemStack {
        val tag = item.getItemTag()
        tag.removeDeep("choten_insurance")
        tag.saveTo(item)

        val meta = item.itemMeta ?: return item
        val current = meta.displayName()
        if (current != null) {
            val serialized = mmStrictUtil.serialize(current)
            val cleaned = serialized.replace(" <gold>[已投保]", "")
            if (cleaned.isNotBlank()) {
                meta.displayName(mmUtil.deserialize(cleaned))
            } else {
                meta.displayName(null as Component?)
            }
        }
        item.itemMeta = meta
        return item
    }

    fun calculateTotalCost(items: List<ItemStack>): Double {
        return items.filter { isInsurable(it) && !isAlreadyInsured(it) }
            .sumOf { getInsuranceCost(it) }
    }

    fun tryInsureItem(player: Player, item: ItemStack): Boolean {
        if (!isInsurable(item) || isAlreadyInsured(item)) {
            devLog("!isInsurable(item) || isAlreadyInsured(item) check failed!")
            return false
        }

        val cost = getInsuranceCost(item)
        if (cost > 0 && !EconomyUtil.withdraw(player, cost)) {
            devLog("cost > 0 && !EconomyUtil.withdraw(player, cost) failed!")
            return false
        }

        val serialized = ItemSerializer.serializeNbt(item) ?: run {
            devLog("serializeNbt failed!")
            return false
        }
        val now = Instant.now().toEpochMilli()
        val uuid = UUID.randomUUID().toString()

        val data = player.getData() ?: return false
        data.insuredItems[uuid] = InsuredItem(
            uuid = uuid,
            itemData = serialized,
            owner = player.name,
            insuredAt = now,
            expiresAt = now + expireDays * 86400000L
        )
        return true
    }

    fun cleanExpired(player: Player) {
        val data = player.getData() ?: return
        val now = Instant.now().toEpochMilli()
        data.insuredItems.entries.removeAll { it.value.expiresAt < now }
    }

    fun getValidInsuredItems(player: Player): List<InsuredItem> {
        val data = player.getData() ?: return emptyList()
        val now = Instant.now().toEpochMilli()
        return data.insuredItems.values.filter { it.expiresAt >= now }
    }

    fun getDropChance(player: Player): Double {
        val base = config.getDouble("death-drop.default-chance", 60.0)
        var modifier = 0.0
        val section = config.getConfigurationSection("death-drop.permission-modifiers") ?: return base.coerceIn(0.0, 100.0)
        for (perm in section.getKeys(false)) {
            if (player.hasPermission(perm)) {
                modifier += section.getDouble(perm, 0.0)
            }
        }
        return (base + modifier).coerceIn(0.0, 100.0)
    }
}
