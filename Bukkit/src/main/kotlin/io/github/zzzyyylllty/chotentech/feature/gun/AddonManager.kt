package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.module.nms.ItemTag
import taboolib.module.nms.getItemTag

/** 已安装配件及有效属性；只合并写 sertraline_data.gun.addons，不触碰 GunState 字段。 */
object AddonManager {
    private const val ROOT = "sertraline_data.gun"

    fun readAddons(held: ItemStack): MutableMap<String, String> {
        val result = linkedMapOf<String, String>()
        try {
            val gun = held.getItemTag().getDeep(ROOT) as? ItemTag ?: return result
            val addons = gun["addons"] as? ItemTag ?: return result
            for (slot in addons.keys) {
                val id = addons[slot]?.asString()?.trim().orEmpty()
                if (id.isNotEmpty()) result[slot.uppercase()] = id
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun writeAddons(held: ItemStack, addons: Map<String, String>): Boolean {
        return try {
            val tag = held.getItemTag()
            val value = ItemTag()
            for ((slot, id) in addons) value[slot.uppercase()] = id
            tag.putDeep("$ROOT.addons", value)
            tag.saveTo(held)
            true
        } catch (e: Exception) {
            devLog("[Gun] 写入配件 NBT 失败: ${e.message}")
            false
        }
    }

    fun installed(held: ItemStack): List<AddonData> =
        readAddons(held).values.mapNotNull(GunRegistry::addon)

    /** 校验并安装；slot 是玩家背包原始槽位。先写 NBT 成功，再消耗一个物品。 */
    fun install(player: Player, held: ItemStack, addonItem: ItemStack, slot: Int): Boolean {
        val gun = GunRegistry.gunOf(held) ?: return false
        val addon = GunRegistry.addonOf(addonItem) ?: return false
        if (!compatible(gun, addon, addon.slot)) return false
        val current = try { player.inventory.getItem(slot) } catch (_: Exception) { null } ?: return false
        if (current.amount <= 0 || GunRegistry.addonOf(current)?.id != addon.id) return false
        val previous = readAddons(held)
        if (previous.containsKey(addon.slot)) return false
        val updated = previous.toMutableMap().apply { this[addon.slot] = addon.id }

        if (!writeAddons(held, updated)) {
            // writeAddons 可能只完成了一部分，尽力恢复旧 map；背包配件绝不扣除。
            writeAddons(held, previous)
            writeBackMainHand(player, held)
            return false
        }
        val consumed = try {
            // 写入后重新核验槽位，物品已变或 setItem 异常都进入 NBT 回滚。
            val latest = player.inventory.getItem(slot)
            if (latest == null || latest.amount <= 0 || GunRegistry.addonOf(latest)?.id != addon.id ||
                (latest !== current && latest.hashCode() != current.hashCode())
            ) {
                false
            } else {
                if (latest.amount <= 1) player.inventory.setItem(slot, null) else {
                    latest.amount--
                    player.inventory.setItem(slot, latest)
                }
                true
            }
        } catch (e: Exception) {
            devLog("[Gun] 扣除配件物品失败: ${e.message}")
            false
        }
        if (!consumed) {
            if (!writeAddons(held, previous)) devLog("[Gun] 配件安装回滚 NBT 失败")
            writeBackMainHand(player, held)
            return false
        }
        writeBackMainHand(player, held)
        return true
    }

    fun uninstall(player: Player, held: ItemStack, slot: String): Boolean {
        val previous = readAddons(held)
        val updated = previous.toMutableMap()
        if (updated.remove(slot.uppercase()) == null) return false
        if (!writeAddons(held, updated)) {
            writeAddons(held, previous)
            writeBackMainHand(player, held)
            return false
        }
        writeBackMainHand(player, held)
        return true
    }

    private fun writeBackMainHand(player: Player, held: ItemStack) {
        try {
            player.inventory.setItemInMainHand(held)
        } catch (e: Exception) {
            devLog("[Gun] 配件写回主手失败: ${e.message}")
        }
    }

    fun compatible(gun: GunData, addon: AddonData, slot: String = addon.slot): Boolean =
        gun.addonSlots.any { it.equals(slot, true) } && addon.slot.equals(slot, true) &&
            (addon.gunType.isEmpty() || addon.gunType.any { it.equals(gun.gunType, true) }) &&
            (addon.caliber == null || addon.caliber.equals(gun.caliber, true))

    fun effectiveAttributes(gun: GunData, held: ItemStack): GunAttributes {
        val base = gun.attributes
        val addons = installed(held)
        fun sum(key: String): Double = addons.sumOf { it.attributes[key] ?: 0.0 }
        return GunAttributes(
            machineEfficiency = (base.machineEfficiency + sum("machineEfficiency")).coerceIn(0.0, 100.0),
            accuracy = (base.accuracy + sum("accuracy")).coerceIn(0.0, 100.0),
            bulletSpeed = (base.bulletSpeed + sum("bulletSpeed")).coerceAtLeast(1.0),
            fireRate = (base.fireRate + sum("fireRate")).coerceAtLeast(1.0),
            barrelLength = (base.barrelLength + sum("barrelLength")).coerceAtLeast(0.0),
            range = (base.range + sum("range")).coerceAtLeast(1.0),
            recoilV = (base.recoilV + sum("recoilV")).coerceAtLeast(0.0),
            recoilH = (base.recoilH + sum("recoilH")).coerceAtLeast(0.0),
            durability = (base.durability + sum("durability")).coerceAtLeast(1.0),
        )
    }

    fun suppressor(held: ItemStack): Double =
        installed(held).map { it.suppressor }.filter { it > 0.0 }.minOrNull() ?: 1.0

    fun zoom(held: ItemStack): Double =
        readAddons(held)["SIGHT"]?.let(GunRegistry::addon)?.zoom ?: 1.0

    fun hasThermal(held: ItemStack): Boolean = installed(held).any { it.thermal }
}
