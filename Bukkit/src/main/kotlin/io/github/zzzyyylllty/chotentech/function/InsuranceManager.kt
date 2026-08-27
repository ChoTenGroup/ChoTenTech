package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.data.InsuredItem
import io.github.zzzyyylllty.chotentech.util.EconomyUtil
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import io.github.zzzyyylllty.chotentech.util.minimessage.mmStrictUtil
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.getItemTag

object InsuranceManager {

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
        val suffix = mmUtil.deserialize(" <#DFBD7C>[已投保]")
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
            val cleaned = serialized.replace(" <#DFBD7C>[已投保]", "")
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

    /**
     * 投保：只扣费并把保险标记打到物品自身 NBT 标签上（choten_insurance），
     * 不存储物品的序列化副本。
     *
     * 保险状态完全由物品身上的 NBT 标签承载 —— 死亡时清除仓库根据该标签把物品
     * 返还给玩家即可。若在这里再存一份完整物品副本，就会与玩家身上的那份形成
     * "一份投保的一份未投保的" 两份物品（刷物品漏洞）。
     */
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

        // 保险只依赖物品自身的标签；不再写入 data.insuredItems，
        // 避免产生一份可归还的未投保副本（刷物品）。
        return true
    }

    fun cleanExpired(player: Player) {
        // 不再存储 InsuredItem 副本，保持方法以兼容旧数据清理调用。
    }

    /**
     * 返回空列表：投保由物品 NBT 标签承担，不允许从玩家数据里再还原出一份副本。
     * 若调用它把副本发回玩家，就会造成 "一份投保 + 一份未投保" 刷物品。
     */
    fun getValidInsuredItems(player: Player): List<InsuredItem> = emptyList()

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
