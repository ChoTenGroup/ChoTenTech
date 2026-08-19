package io.github.zzzyyylllty.chotentech.station

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.CurrencyManager
import io.github.zzzyyylllty.chotentech.function.ReputationManager
import io.github.zzzyyylllty.chotentech.kether.evalKetherBoolean
import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.shop.CostType
import io.github.zzzyyylllty.chotentech.shop.ShopMenu
import io.github.zzzyyylllty.chotentech.util.ExternalItemHelper
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import java.io.File

object StationManager {

    val stations = linkedMapOf<String, StationConfig>()

    // ── 配置加载 ─────────────────────────────────────────────────

    fun loadAll() {
        stations.clear()
        val dir = File(getDataFolder(), "stations")
        if (!dir.isDirectory) return
        dir.listFiles { f -> f.name.endsWith(".yml", ignoreCase = true) }?.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                for (id in config.getKeys(false)) {
                    config.getConfigurationSection(id)?.let { loadStation(id, it) }
                }
            } catch (e: Exception) {
                warning("加载合成站 ${file.name} 失败: ${e.message}")
            }
        }
    }

    private fun loadStation(id: String, sec: ConfigurationSection) {
        val extraQueues = mutableListOf<StationExtraQueue>()
        sec.getConfigurationSection("extra-queue")?.getKeys(false)?.forEach { key ->
            val eqSec = sec.getConfigurationSection("extra-queue.$key") ?: return@forEach
            val affection = eqSec.getConfigurationSection("affection")?.let { affSec ->
                StationAffection(
                    id = affSec.getString("id") ?: "",
                    required = affSec.getLong("required", 0L),
                )
            }
            extraQueues += StationExtraQueue(
                key = key,
                display = eqSec.getString("display"),
                affection = affection,
                permission = eqSec.getString("permission"),
                slots = eqSec.getInt("slots", 0),
            )
        }

        val recipes = linkedMapOf<String, StationRecipe>()
        sec.getConfigurationSection("recipes")?.getKeys(false)?.forEach { rKey ->
            val rSec = sec.getConfigurationSection("recipes.$rKey") ?: return@forEach
            // 制作条件: condition.kether 满足才可制作, display 用于展示文案
            val condition = rSec.getConfigurationSection("condition")?.let { cSec ->
                StationCondition(
                    kether = cSec.getString("kether"),
                    display = cSec.getString("display"),
                )
            }
            recipes[rKey] = StationRecipe(
                key = rKey,
                output = rSec.getString("id") ?: rKey,
                buy = rSec.getStringList("buy").map { parseBuyCost(it) },
                time = rSec.getInt("time", 0),
                condition = condition,
            )
        }

        stations[id] = StationConfig(
            id = id,
            name = sec.getString("name") ?: id,
            queue = sec.getInt("queue", 0),
            extraQueues = extraQueues,
            recipes = recipes,
        )
    }

    /**
     * 解析 buy 字符串: "money:100" → 货币; "sertraline:iron_ingot:2" → 物品 ×2;
     * "IRON_INGOT:3" → 材质 ×3; "IRON_INGOT" → 材质 ×1
     */
    private fun parseBuyCost(str: String): StationCost {
        val parts = str.split(":")
        // 首段是货币类型 → 纯货币消耗
        if (parts.isNotEmpty() && parts[0] in CurrencyManager.types) {
            return StationCost(
                type = CostType.CURRENCY,
                currency = parts[0],
                amount = parts.getOrNull(1)?.toLongOrNull() ?: 1L,
            )
        }
        // 末段是数量 → "sertraline:iron_ingot:2" / "IRON_INGOT:3"
        val lastAmount = parts.lastOrNull()?.toLongOrNull()
        if (lastAmount != null && parts.size >= 2) {
            return StationCost(
                type = CostType.ITEM,
                item = parts.dropLast(1).joinToString(":"),
                amount = lastAmount,
            )
        }
        // 整体就是物品 id
        return StationCost(type = CostType.ITEM, item = str, amount = 1L)
    }

    // ── 查询 ─────────────────────────────────────────────────────

    fun getStation(id: String): StationConfig? = stations[id]

    fun getRecipe(station: StationConfig, key: String): StationRecipe? = station.recipes[key]

    fun getRecipes(station: StationConfig): List<StationRecipe> = station.recipes.values.toList()

    /** 队列容量 = 基础 queue + 满足条件的 extra-queue 槽位 */
    fun getMaxQueueSlots(player: Player, station: StationConfig): Int {
        var slots = station.queue
        for (eq in station.extraQueues) {
            val affOk = eq.affection == null ||
                ReputationManager.getAffection(player, eq.affection.id) >= eq.affection.required
            val permOk = eq.permission == null || player.hasPermission(eq.permission)
            if (affOk && permOk) slots += eq.slots
        }
        return maxOf(slots, 0)
    }

    fun getQueue(player: Player, stationId: String): MutableList<StationQueueItem> {
        val data = player.getData() ?: return mutableListOf()
        return data.stationQueues.getOrPut(stationId) { mutableListOf() }
    }

    /** 全部消耗(材料+货币)是否满足 */
    fun canAfford(player: Player, recipe: StationRecipe): Boolean {
        for (cost in recipe.buy) {
            when (cost.type) {
                CostType.CURRENCY -> {
                    if (!CurrencyManager.hasBalance(player, cost.currency ?: "money", cost.amount)) return false
                }
                CostType.ITEM -> {
                    if (countItem(player, cost.item ?: "") < cost.amount) return false
                }
            }
        }
        return true
    }

    /** 材料物品是否满足(不看货币) */
    fun hasMaterials(player: Player, recipe: StationRecipe): Boolean {
        for (cost in recipe.buy) {
            if (cost.type == CostType.ITEM && countItem(player, cost.item ?: "") < cost.amount) return false
        }
        return true
    }

    /** 配方制作条件是否满足(无 kether 条件时视为满足) */
    fun conditionMet(player: Player, recipe: StationRecipe): Boolean {
        return recipe.condition?.kether.evalKetherBoolean(player, linkedMapOf("@Player" to player))
    }

    // ── 制作 / 领取 ──────────────────────────────────────────────

    fun startCrafting(player: Player, station: StationConfig, recipe: StationRecipe): Boolean {
        val queue = getQueue(player, station.id)
        val maxSlots = getMaxQueueSlots(player, station)
        if (queue.size >= maxSlots) {
            player.sendComponent("<red>合成站队列已满! 领取完成的物品后可继续制作")
            return false
        }

        // 校验制作条件(kether)
        val cond = recipe.condition
        if (cond != null && !conditionMet(player, recipe)) {
            val desc = cond.display?.replace('\n', ' ') ?: "该配方的制作条件"
            player.sendComponent("<red>制作条件未满足: <yellow>$desc")
            return false
        }

        // 校验消耗
        for (cost in recipe.buy) {
            when (cost.type) {
                CostType.CURRENCY -> {
                    val cur = cost.currency ?: "money"
                    if (!CurrencyManager.hasBalance(player, cur, cost.amount)) {
                        player.sendComponent("<red>缺少货币: ${CurrencyManager.displayName(cur)} x${cost.amount}")
                        return false
                    }
                }
                CostType.ITEM -> {
                    val id = cost.item ?: continue
                    val have = countItem(player, id)
                    if (have < cost.amount) {
                        player.sendComponent("<red>缺少材料: ${displayName(id)} x${cost.amount} (现有 $have)")
                        return false
                    }
                }
            }
        }

        // 扣除消耗
        for (cost in recipe.buy) {
            when (cost.type) {
                CostType.CURRENCY -> CurrencyManager.removeBalance(player, cost.currency ?: "money", cost.amount)
                CostType.ITEM -> takeItem(player, cost.item ?: "", cost.amount)
            }
        }

        queue += StationQueueItem(recipe.key, System.currentTimeMillis(), recipe.time)
        player.sendComponent("<green>开始制作: <yellow>${displayName(recipe.output)} <gray>(需 ${recipe.time} 秒)")
        player.playSound(player.location, Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f)
        return true
    }

    /** 领取已完成的队列任务,成功返回 true */
    fun claim(player: Player, station: StationConfig, entry: StationQueueItem): Boolean {
        if (!entry.isComplete) {
            player.sendComponent("<red>该物品仍在制作中, 剩余 ${entry.remainingTime} 秒")
            return false
        }
        val queue = getQueue(player, station.id)
        val recipe = station.recipes[entry.recipeId] ?: run {
            queue.remove(entry)
            player.sendComponent("<red>配方已失效, 已移除该队列任务")
            return false
        }
        val item = buildOutputItem(player, recipe) ?: run {
            // 产出无法构建时同样移除队列任务, 避免该槽位永久被占满无法继续制作
            queue.remove(entry)
            player.sendComponent("<red>产出物品无法构建, 已移除该队列任务, 请联系管理员")
            return false
        }
        val given = giveItem(player, item, recipe.output)
        // giveItem 背包放不下的会掉在地上,物品必定交付,因此始终从队列移除,防止重复领取
        queue.remove(entry)
        if (given > 0) {
            player.sendComponent("<green>领取成功: <yellow>${displayName(recipe.output)} <green>x$given")
        } else {
            player.sendComponent("<yellow>${displayName(recipe.output)} <green>已领取(背包已满, 物品掉落在脚边)")
        }
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        return true
    }

    // ── 物品工具(与商店逻辑一致) ─────────────────────────────────

    fun buildOutputItem(player: Player, recipe: StationRecipe): ItemStack? {
        val output = recipe.output
        val colon = output.lastIndexOf(':')
        val suffix = if (colon >= 0) output.substring(colon + 1) else output

        try {
            io.github.zzzyyylllty.sertraline.Sertraline.api().buildItem(suffix, player, amount = 1)
                ?.let { if (!it.type.isAir) return it }
        } catch (_: Exception) { /* 非 sertraline 物品 */ }

        if (colon >= 0) {
            ExternalItemHelper.build(player, output.substring(0, colon), suffix)
                ?.let { if (!it.type.isAir) return it }
        }

        try {
            Material.valueOf(output.uppercase()).let { return ItemStack(it) }
        } catch (_: Exception) { /* 不是材质 */ }
        try {
            XMaterial.valueOf(output.uppercase()).parseItem()?.let { return it }
        } catch (_: Exception) { /* 不是 xmaterial */ }
        return null
    }

    fun displayName(itemId: String): String = ShopMenu.lookupItemName(itemId)

    private fun matchesItemId(item: ItemStack, itemId: String): Boolean {
        try {
            val sertralineId = io.github.zzzyyylllty.sertraline.Sertraline.api().getId(item)
            if (sertralineId != null) {
                if (sertralineId == itemId) return true
                val colonIdx = itemId.lastIndexOf(':')
                if (colonIdx >= 0 && sertralineId == itemId.substring(colonIdx + 1)) return true
            }
        } catch (_: Exception) { /* fall through */ }

        val materialName = item.type.name
        if (materialName.equals(itemId.uppercase(), ignoreCase = true)) return true
        val colonIdx = itemId.lastIndexOf(':')
        if (colonIdx >= 0) {
            val suffix = itemId.substring(colonIdx + 1)
            if (materialName.equals(suffix.uppercase(), ignoreCase = true)) return true
        }
        return false
    }

    fun countItem(player: Player, itemId: String): Long {
        var count = 0L
        // 只统计主背包(36 格), 避免把护甲/副手也当作材料扣除
        for (item in player.inventory.storageContents) {
            if (item == null || item.type.isAir) continue
            if (matchesItemId(item, itemId)) count += item.amount
        }
        return count
    }

    private fun takeItem(player: Player, itemId: String, amount: Long) {
        var remaining = amount
        for (i in player.inventory.storageContents.indices) {
            val item = player.inventory.getItem(i) ?: continue
            if (item.type.isAir) continue
            if (!matchesItemId(item, itemId)) continue

            if (item.amount <= remaining) {
                remaining -= item.amount
                player.inventory.setItem(i, null)
            } else {
                item.amount = (item.amount - remaining).toInt()
                player.inventory.setItem(i, item)
                remaining = 0
            }
            if (remaining <= 0) break
        }
    }

    private fun giveItem(player: Player, template: ItemStack, outputId: String? = null): Int {
        val maxStack = template.maxStackSize
        var remaining = template.amount
        var given = 0
        while (remaining > 0) {
            val stackSize = minOf(remaining, maxStack)
            val give = template.clone()
            give.amount = stackSize
            val leftover = player.inventory.addItem(give)
            val added = stackSize - (leftover.values.firstOrNull()?.amount ?: 0)
            given += added
            remaining -= added
            if (leftover.isNotEmpty()) break
        }
        if (remaining > 0) {
            // 掉落提示使用配方显示名(outputId), 避免显示材质英文名
            val name = outputId?.let { displayName(it) } ?: displayName(template.type.name)
            player.sendComponent("<red>背包已满! 有 $remaining 个 <yellow>$name <red>掉落在了地上")
            player.world.dropItem(player.location, template.clone().apply { amount = remaining })
        }
        return given
    }
}
