package io.github.zzzyyylllty.chotentech.function.noclip

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.listener.checkPlayerIsInNoclipPoint
import io.github.zzzyyylllty.chotentech.shop.ShopMenu
import io.github.zzzyyylllty.chotentech.util.CurrencyUtil
import io.github.zzzyyylllty.chotentech.util.toComponent
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.chotentech.function.kether.evalKetherBoolean
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

sealed class ConditionResult {
    data object Allowed : ConditionResult()
    data class Blocked(val message: String?) : ConditionResult()
    data class NeedsSubmission(val message: String?) : ConditionResult()
}

object NoclipConditionChecker {

    fun checkConditions(player: Player, noclipPoint: NoclipPoint): ConditionResult {
        // 检查是否有电梯关联到此切出点且电源未激活
        if (!PowerManager.hasElevatorPowerActiveFor(noclipPoint)) {
            return ConditionResult.Blocked("需要激活电源")
        }

        val conditions = noclipPoint.conditions ?: return ConditionResult.Allowed

        conditions.item?.let {
            val result = checkItemCondition(player, noclipPoint, it)
            if (result !is ConditionResult.Allowed) return result
        }

        conditions.car?.let {
            val result = checkCarCondition(player, noclipPoint, it)
            if (result !is ConditionResult.Allowed) return result
        }

        conditions.coop?.let {
            val result = checkCoopCondition(player, noclipPoint, it)
            if (result !is ConditionResult.Allowed) return result
        }

        conditions.signal?.let {
            val result = checkSignalCondition(player, noclipPoint, it)
            if (result !is ConditionResult.Allowed) return result
        }

        conditions.dropbag?.let {
            val result = checkDropbagCondition(player, it)
            if (result !is ConditionResult.Allowed) return result
        }

        conditions.condition?.let {
            val result = checkConditionChain(player, it)
            if (result !is ConditionResult.Allowed) return result
        }

        return ConditionResult.Allowed
    }

    fun needsSubmission(noclipPoint: NoclipPoint): Boolean {
        val c = noclipPoint.conditions ?: return false
        return c.item != null || c.car != null
    }

    /** 获取切出点当前最相关的条件提示文本(与 actionbar 提示一致);条件已满足或无条件时返回 null */
    fun getConditionHint(player: Player, noclipPoint: NoclipPoint): String? {
        val result = checkConditions(player, noclipPoint)
        return when (result) {
            is ConditionResult.Blocked -> result.message
            is ConditionResult.NeedsSubmission -> result.message
            else -> null
        }
    }

    /** 构建切出点条件指引聊天文本:告诉玩家如何提交物品满足条件 */
    fun buildConditionGuide(noclipPoint: NoclipPoint): String {
        val c = noclipPoint.conditions ?: return "<#e8d9ba>此处切出需要满足条件"
        val sb = StringBuilder("<#e8d9ba>切出点 <#F2F5FA>${noclipPoint.name ?: "未命名"}<#e8d9ba> 需要满足条件:")
        c.item?.let { cond ->
            val items = cond.items.joinToString(", ") { "${ShopMenu.lookupItemName(it.id)} x${it.amount}" }
            sb.append("\n<#A9B4C3>- <#84D1CB>提交物品: <#F2F5FA>$items <#A9B4C3>(携带所需物品站在切出点内,低头下蹲切出后自动提交)")
        }
        c.car?.let { cond ->
            sb.append("\n<#A9B4C3>- <#84D1CB>支付: <#F2F5FA>${cond.cost} x${cond.amount} <#A9B4C3>(下蹲时自动扣除)")
        }
        c.coop?.let { cond ->
            sb.append("\n<#A9B4C3>- <#84D1CB>合作: <#F2F5FA>需要 ${cond.require} 人同时站在切出点内")
        }
        c.signal?.let {
            sb.append("\n<#A9B4C3>- <#84D1CB>信号: <#F2F5FA>在指定区域手持信号物品")
        }
        c.dropbag?.let {
            sb.append("\n<#A9B4C3>- <#84D1CB>空包: <#F2F5FA>清空指定栏位后切出")
        }
        c.condition?.let {
            sb.append("\n<#A9B4C3>- <#84D1CB>条件: <#F2F5FA>满足特殊条件")
        }
        return sb.toString()
    }

    private fun unlockKey(player: Player, noclipPoint: NoclipPoint): String {
        val levelId = ChoTenTech.playerDataMap[player.uniqueId.toString()]?.activeLevelStr ?: ""
        return "${player.uniqueId}::$levelId::${noclipPoint.name}"
    }

    private fun sharedKey(player: Player, noclipPoint: NoclipPoint): String {
        val levelId = ChoTenTech.playerDataMap[player.uniqueId.toString()]?.activeLevelStr ?: ""
        return "$levelId::${noclipPoint.name}"
    }

    private fun lifetimeKey(player: Player, noclipPoint: NoclipPoint): String {
        val levelId = ChoTenTech.playerDataMap[player.uniqueId.toString()]?.activeLevelStr ?: ""
        return "$levelId::${noclipPoint.name}"
    }

    // ---- Item Condition ----

    private fun checkItemCondition(player: Player, noclipPoint: NoclipPoint, cond: ItemCondition): ConditionResult {
        val data = ChoTenTech.playerDataMap[player.uniqueId.toString()] ?: return ConditionResult.Blocked(cond.message)

        if (cond.lifetime && data.unlockedNoclips.contains(lifetimeKey(player, noclipPoint)))
            return ConditionResult.Allowed

        if (cond.shared) {
            val session = ChoTenTech.sharedNoclipSessions[sharedKey(player, noclipPoint)]
            if (session?.unlocked == true) return ConditionResult.Allowed
        }

        if (ChoTenTech.sessionUnlockedNoclips[unlockKey(player, noclipPoint)] == true)
            return ConditionResult.Allowed

        return ConditionResult.NeedsSubmission(cond.message)
    }

    fun trySubmitItems(player: Player, noclipPoint: NoclipPoint): ConditionResult {
        val cond = noclipPoint.conditions?.item ?: return ConditionResult.Allowed
        val data = ChoTenTech.playerDataMap[player.uniqueId.toString()] ?: return ConditionResult.Blocked(cond.message)

        if (cond.lifetime && data.unlockedNoclips.contains(lifetimeKey(player, noclipPoint)))
            return ConditionResult.Allowed

        if (cond.shared) {
            val session = ChoTenTech.sharedNoclipSessions[sharedKey(player, noclipPoint)]
            if (session?.unlocked == true) return ConditionResult.Allowed
        }

        if (ChoTenTech.sessionUnlockedNoclips[unlockKey(player, noclipPoint)] == true)
            return ConditionResult.Allowed

        for (ci in cond.items) {
            if (!hasItem(player, ci.id, ci.amount)) {
                return ConditionResult.Blocked(cond.message)
            }
        }

        for (ci in cond.items) {
            if (ci.consume) {
                removeItem(player, ci.id, ci.amount)
            }
        }

        val key = unlockKey(player, noclipPoint)

        if (cond.shared) {
            val sk = sharedKey(player, noclipPoint)
            val session = ChoTenTech.sharedNoclipSessions.getOrPut(sk) { SharedNoclipSession() }
            session.paidBy.add(player.uniqueId.toString())
            session.unlocked = true
        }

        if (cond.lifetime) {
            data.unlockedNoclips.add(lifetimeKey(player, noclipPoint))
        }

        if (!cond.lifetime) {
            ChoTenTech.sessionUnlockedNoclips[key] = true
        }

        player.sendActionBar("<#8FD4A6>允许切出".toComponent())
        return ConditionResult.Allowed
    }

    private fun hasItem(player: Player, sertralineId: String, amount: Int): Boolean {
        var count = 0
        for (item in player.inventory) {
            if (item == null) continue
            val id = Sertraline.api().getId(item)
            if (id == sertralineId) count += item.amount
        }
        return count >= amount
    }

    private fun removeItem(player: Player, sertralineId: String, amount: Int) {
        var remaining = amount
        val inv = player.inventory
        for (i in 0 until inv.size) {
            val item = inv.getItem(i) ?: continue
            if (Sertraline.api().getId(item) == sertralineId) {
                val toRemove = minOf(remaining, item.amount)
                if (toRemove >= item.amount) {
                    inv.setItem(i, null)
                } else {
                    item.amount -= toRemove
                }
                remaining -= toRemove
                if (remaining <= 0) break
            }
        }
    }

    // ---- Car Condition ----

    private fun checkCarCondition(player: Player, noclipPoint: NoclipPoint, cond: CarCondition): ConditionResult {
        val data = ChoTenTech.playerDataMap[player.uniqueId.toString()] ?: return ConditionResult.Blocked(cond.message)

        if (cond.lifetime && data.unlockedNoclips.contains(lifetimeKey(player, noclipPoint)))
            return ConditionResult.Allowed

        if (cond.shared) {
            val session = ChoTenTech.sharedNoclipSessions[sharedKey(player, noclipPoint)]
            if (session?.unlocked == true) return ConditionResult.Allowed
        }

        if (ChoTenTech.sessionUnlockedNoclips[unlockKey(player, noclipPoint)] == true)
            return ConditionResult.Allowed

        return ConditionResult.NeedsSubmission(cond.message)
    }

    fun trySubmitCar(player: Player, noclipPoint: NoclipPoint): ConditionResult {
        val cond = noclipPoint.conditions?.car ?: return ConditionResult.Allowed
        val data = ChoTenTech.playerDataMap[player.uniqueId.toString()] ?: return ConditionResult.Blocked(cond.message)

        if (cond.lifetime && data.unlockedNoclips.contains(lifetimeKey(player, noclipPoint)))
            return ConditionResult.Allowed

        if (cond.shared) {
            val session = ChoTenTech.sharedNoclipSessions[sharedKey(player, noclipPoint)]
            if (session?.unlocked == true) return ConditionResult.Allowed
        }

        if (ChoTenTech.sessionUnlockedNoclips[unlockKey(player, noclipPoint)] == true)
            return ConditionResult.Allowed

        val success = when {
            CurrencyUtil.getCurrency(cond.cost) != null ->
                CurrencyUtil.remove(player, cond.cost, cond.amount.toDouble())
            else ->
                removeBuiltinCurrency(data, cond.cost, cond.amount)
        }
        if (!success) return ConditionResult.Blocked(cond.message)

        val key = unlockKey(player, noclipPoint)

        if (cond.shared) {
            val sk = sharedKey(player, noclipPoint)
            val session = ChoTenTech.sharedNoclipSessions.getOrPut(sk) { SharedNoclipSession() }
            session.paidBy.add(player.uniqueId.toString())
            session.unlocked = true
        }

        if (cond.lifetime) {
            data.unlockedNoclips.add(lifetimeKey(player, noclipPoint))
        }

        if (!cond.lifetime) {
            ChoTenTech.sessionUnlockedNoclips[key] = true
        }

        player.sendActionBar("<#8FD4A6>允许切出".toComponent())
        return ConditionResult.Allowed
    }

    private fun removeBuiltinCurrency(data: PlayerData, currencyId: String, amount: Long): Boolean {
        return when (currencyId.lowercase()) {
            "money" -> { if (data.money >= amount) { data.money -= amount; true } else false }
            "point" -> { if (data.point >= amount) { data.point -= amount; true } else false }
            "tape" -> { if (data.tape >= amount) { data.tape -= amount; true } else false }
            "ltoken" -> { if (data.ltoken >= amount) { data.ltoken -= amount; true } else false }
            "ptoken" -> { if (data.ptoken >= amount) { data.ptoken -= amount; true } else false }
            else -> false
        }
    }

    // ---- Coop Condition ----

    private fun checkCoopCondition(player: Player, noclipPoint: NoclipPoint, cond: CoopCondition): ConditionResult {
        val count = Bukkit.getOnlinePlayers().count { p ->
            p.uniqueId != player.uniqueId && checkPlayerIsInNoclipPoint(p, noclipPoint)
        }
        val total = count + 1
        if (total < cond.require) {
            val msg = cond.message?.replace("%current%", total.toString())?.replace("%max%", cond.max.toString())?.replace("%require%", cond.require.toString())
            return ConditionResult.Blocked(msg)
        }
        if (cond.max > 0 && total > cond.max) {
            val msg = cond.message?.replace("%current%", total.toString())?.replace("%max%", cond.max.toString())?.replace("%require%", cond.require.toString())
            return ConditionResult.Blocked(msg)
        }
        return ConditionResult.Allowed
    }

    // ---- Signal Condition ----

    private fun checkSignalCondition(player: Player, noclipPoint: NoclipPoint, cond: SignalCondition): ConditionResult {
        if (ChoTenTech.sessionUnlockedNoclips[unlockKey(player, noclipPoint)] == true)
            return ConditionResult.Allowed

        return ConditionResult.Blocked(cond.message)
    }

    fun trySignal(player: Player, noclipPoint: NoclipPoint, handItem: ItemStack): Boolean {
        val cond = noclipPoint.conditions?.signal ?: return false
        val itemId = Sertraline.api().getId(handItem) ?: return false

        val requiredItems = when (cond.item) {
            is String -> listOf(cond.item)
            is List<*> -> cond.item.filterIsInstance<String>()
            else -> return false
        }

        if (itemId !in requiredItems) return false

        if (cond.world != null && cond.location1 != null && cond.location2 != null) {
            val loc = player.location
            if (loc.world?.name != cond.world) return false
            if (!isInBox(loc.x, loc.y, loc.z, cond.location1, cond.location2)) return false
        }

        player.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f)

        val key = unlockKey(player, noclipPoint)
        ChoTenTech.sessionUnlockedNoclips[key] = true

        player.sendActionBar("<#8FD4A6>信号已发送，现在可以切出了。".toComponent())
        return true
    }

    // ---- Dropbag Condition ----

    private fun checkDropbagCondition(player: Player, cond: DropbagCondition): ConditionResult {
        val inv = player.inventory
        val armorSlots = mapOf(
            "helmet" to 39, "chestplate" to 38, "leggings" to 37, "boots" to 36
        )
        for ((slotName, slotIndex) in armorSlots) {
            val allowed = when (slotName) {
                "helmet" -> cond.helmet ?: cond.armor ?: false
                "chestplate" -> cond.chestplate ?: cond.armor ?: false
                "leggings" -> cond.leggings ?: cond.armor ?: false
                "boots" -> cond.boots ?: cond.armor ?: false
                else -> cond.armor ?: false
            }
            if (!allowed && inv.getItem(slotIndex) != null && !inv.getItem(slotIndex)!!.type.isAir) {
                return ConditionResult.Blocked(cond.message)
            }
        }

        val invAllowed = cond.inventory ?: false
        if (!invAllowed) {
            for (i in 9..35) {
                val item = inv.getItem(i) ?: continue
                if (!item.type.isAir) {
                    return ConditionResult.Blocked(cond.message)
                }
            }
        }

        if (!cond.hotbar) {
            for (i in 0..8) {
                val item = inv.getItem(i) ?: continue
                if (!item.type.isAir) {
                    return ConditionResult.Blocked(cond.message)
                }
            }
        }

        return ConditionResult.Allowed
    }

    // ---- Condition Chain ----

    private fun checkConditionChain(player: Player, chain: List<ConditionChainEntry>): ConditionResult {
        for (entry in chain) {
            if (entry.kether != null) {
                val result = entry.kether.evalKetherBoolean(player, linkedMapOf("@Player" to player))
                if (result == entry.yes) {
                    return if (entry.yes) ConditionResult.Allowed
                    else ConditionResult.Blocked(entry.message)
                }
            } else {
                return if (entry.yes) ConditionResult.Allowed
                else ConditionResult.Blocked(entry.message)
            }
        }
        return ConditionResult.Allowed
    }

    // ---- Drop items on successful noclip for dropbag ----

    fun enforceDropbagOnSuccess(player: Player, noclipPoint: NoclipPoint) {
        val cond = noclipPoint.conditions?.dropbag ?: return
        val inv = player.inventory
        val armorSlots = mapOf(
            "helmet" to 39, "chestplate" to 38, "leggings" to 37, "boots" to 36
        )
        val dropItems = mutableListOf<ItemStack>()

        for ((slotName, slotIndex) in armorSlots) {
            val allowed = when (slotName) {
                "helmet" -> cond.helmet ?: cond.armor ?: false
                "chestplate" -> cond.chestplate ?: cond.armor ?: false
                "leggings" -> cond.leggings ?: cond.armor ?: false
                "boots" -> cond.boots ?: cond.armor ?: false
                else -> cond.armor ?: false
            }
            if (!allowed) {
                val item = inv.getItem(slotIndex)
                if (item != null && !item.type.isAir) {
                    dropItems.add(item)
                    inv.setItem(slotIndex, null)
                }
            }
        }

        val invAllowed = cond.inventory ?: false
        if (!invAllowed) {
            for (i in 9..35) {
                val item = inv.getItem(i) ?: continue
                if (!item.type.isAir) {
                    dropItems.add(item)
                    inv.setItem(i, null)
                }
            }
        }

        if (!cond.hotbar) {
            for (i in 0..8) {
                val item = inv.getItem(i) ?: continue
                if (!item.type.isAir) {
                    dropItems.add(item)
                    inv.setItem(i, null)
                }
            }
        }

        for (item in dropItems) {
            player.world.dropItemNaturally(player.location, item)
        }
    }

    private fun isInBox(x: Double, y: Double, z: Double, v1: org.bukkit.util.Vector, v2: org.bukkit.util.Vector): Boolean {
        val xmin = minOf(v1.x, v2.x)
        val xmax = maxOf(v1.x, v2.x)
        val ymin = minOf(v1.y, v2.y)
        val ymax = maxOf(v1.y, v2.y)
        val zmin = minOf(v1.z, v2.z)
        val zmax = maxOf(v1.z, v2.z)
        return x in xmin..xmax && y in ymin..ymax && z in zmin..zmax
    }
}
