package io.github.zzzyyylllty.chotentech.function.hideout

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.player.sendComponent
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.util.ItemSerializer
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor

object HideoutManager {

    private var currentTick = 0L
    private var hideoutTask: PlatformExecutor.PlatformTask? = null

    fun getCurrentBaseKey(player: Player): String {
        return player.getData()?.subTag ?: "global"
    }

    fun getHideout(player: Player): HideoutPlayerData? {
        val data = player.getData() ?: return null
        val baseKey = getCurrentBaseKey(player)
        return data.hideoutData[baseKey]
    }

    fun ensureHideout(player: Player): HideoutPlayerData? {
        val data = player.getData() ?: return null
        val baseKey = getCurrentBaseKey(player)
        return data.hideoutData.getOrPut(baseKey) { HideoutPlayerData() }
    }

    fun ensureHideoutByKey(data: PlayerData, baseKey: String): HideoutPlayerData {
        return data.hideoutData.getOrPut(baseKey) { HideoutPlayerData() }
    }

    fun getTotalUsage(hideout: HideoutPlayerData): Double {
        if (!hideout.unlocked) return 0.0
        var usage = 0.0
        for ((moduleId, level) in hideout.moduleLevels) {
            if (level <= 0) continue
            val mod = HideoutConfig.getModule(moduleId) ?: continue
            usage += mod.usage

            if (moduleId == "crafting_station" && hideout.craftingQueues.isNotEmpty()) {
                val recipe = hideout.craftingQueues.firstOrNull()
                    ?.let { HideoutConfig.getRecipe(it.recipeId) }
                if (recipe != null) usage += recipe.usageCost
            }
            if (moduleId == "drink_station" && hideout.craftingQueues.isNotEmpty()) {
                val recipe = hideout.craftingQueues.firstOrNull()
                    ?.let { HideoutConfig.getRecipe(it.recipeId) }
                if (recipe != null) usage += recipe.usageCost
            }
        }
        return usage
    }

    fun getFuelSavePercent(hideout: HideoutPlayerData): Int {
        val level = hideout.getModuleLevel("power_supply")
        if (level <= 0) return 0
        return HideoutConfig.getModule("power_supply")
            ?.getLevelConfig(level)
            ?.fuelSavePercent
            ?: 0
    }

    fun getEffectiveUsage(hideout: HideoutPlayerData): Double {
        val base = getTotalUsage(hideout)
        val save = getFuelSavePercent(hideout)
        return base * (100 - save) / 100.0
    }

    private fun consumeFuelFromInventory(hideout: HideoutPlayerData): Boolean {
        if (hideout.fuelInventory.isEmpty()) return false

        val slot = hideout.fuelInventory.entries.firstOrNull { (_, itemStr) ->
            val item = ItemSerializer.deserializeNbt(itemStr) ?: return@firstOrNull false
            val fuel = HideoutConfig.getFuelTypeByItem(item)
            fuel != null && fuel.burnTime > 0
        } ?: return false

        val item = ItemSerializer.deserializeNbt(slot.value) ?: return false
        val fuel = HideoutConfig.getFuelTypeByItem(item) ?: return false
        val genLevel = hideout.getModuleLevel("generator")
        val genCfg = HideoutConfig.getModule("generator")?.getLevelConfig(genLevel)

        val efficiency = genCfg?.fuelEfficiency ?: 1.0
        hideout.fuelAmount += fuel.burnTime * efficiency
        hideout.fuelInventory.remove(slot.key)

        item.amount--
        if (item.amount > 0) {
            ItemSerializer.serializeNbt(item)?.let { hideout.fuelInventory[slot.key] = it }
        }

        val capacity = getFuelCapacity(hideout)
        if (hideout.fuelAmount > capacity) hideout.fuelAmount = capacity

        return true
    }

    fun getFuelCapacity(hideout: HideoutPlayerData): Double {
        val genLevel = hideout.getModuleLevel("generator")
        val genCfg = HideoutConfig.getModule("generator")?.getLevelConfig(genLevel)
        return genCfg?.fuelCapacity ?: 0.0
    }

    private fun isGeneratorRunning(hideout: HideoutPlayerData): Boolean {
        return hideout.isModuleBuilt("generator") && hideout.fuelAmount > 0
    }

    fun getMaxGpuSlots(hideout: HideoutPlayerData): Int {
        val level = hideout.getModuleLevel("mine")
        if (level <= 0) return 0
        return HideoutConfig.getModule("mine")
            ?.getLevelConfig(level)
            ?.gpuSlots
            ?: 0
    }

    @Awake(LifeCycle.ACTIVE)
    fun hideoutTimer() {
        hideoutTask = submitAsync(period = 20) {
            currentTick++
            for (player in Bukkit.getOnlinePlayers()) {
                val data = playerDataMap[player.uniqueId.toString()] ?: continue
                val baseKey = data.subTag ?: continue
                val hideout = data.hideoutData[baseKey] ?: continue
                if (!hideout.unlocked) continue

                // 处理等待中的升级 (不依赖发电机)
                if (currentTick % 20 == 0L) {
                    processPendingUpgrades(player, hideout)
                }

                if (!hideout.isModuleBuilt("generator")) continue

                val usage = getEffectiveUsage(hideout)
                if (usage <= 0) continue

                val fuelConsumption = usage / 20.0
                if (hideout.fuelAmount >= fuelConsumption) {
                    hideout.fuelAmount -= fuelConsumption
                } else {
                    hideout.fuelAmount = 0.0
                    if (currentTick % 20 == 0L) {
                        consumeFuelFromInventory(hideout)
                    }
                }

                if (hideout.fuelAmount > 0) {
                    processCrafting(player, hideout)
                    processAutoProduction(player, hideout)
                    processMine(player, hideout)
                }
            }
        }
    }

    private fun processCrafting(player: Player, hideout: HideoutPlayerData) {
        if (hideout.craftingQueues.isEmpty()) return

        val completed = hideout.craftingQueues.filter { it.isComplete }
        if (completed.isEmpty()) return

        hideout.craftingQueues.removeAll(completed.toSet())

        for (queue in completed) {
            val recipe = HideoutConfig.getRecipe(queue.recipeId) ?: continue
            for (output in recipe.outputs) {
                val item = output.toItemStack() ?: continue
                val remaining = player.inventory.addItem(item)
                remaining.values.forEach { leftover ->
                    player.world.dropItem(player.location, leftover)
                }
            }
            player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1f, 1f)
            player.sendComponent("<green>合成完成: <yellow>${recipe.id}")
        }
    }

    private fun processAutoProduction(player: Player, hideout: HideoutPlayerData) {
        // 卫生间、厨房、杏仁集水器、医疗站、情报站
        val producerModules = listOf("restroom", "kitchen", "almond_collector", "medical_station", "intelligence_station")
        for (modId in producerModules) {
            val level = hideout.getModuleLevel(modId)
            if (level <= 0) continue
            val cfg = HideoutConfig.getModule(modId)?.getLevelConfig(level) ?: continue
            if (cfg.productionRate <= 0) continue

            val acc = hideout.productionAccumulators.getOrPut(modId) { 0.0 }
            hideout.productionAccumulators[modId] = acc + cfg.productionRate / 20.0

            val current = hideout.productionAccumulators[modId] ?: continue
            if (current >= 1.0 && cfg.productionItem != null) {
                val whole = current.toInt()
                if (whole > 0) {
                    hideout.productionAccumulators[modId] = current - whole
                    val item = cfg.productionItem?.let { id ->
                        io.github.zzzyyylllty.sertraline.Sertraline.api().buildItem(id, player, amount = whole)
                    }
                    if (item != null) {
                        val remaining = player.inventory.addItem(item)
                        remaining.values.forEach { leftover ->
                            player.world.dropItem(player.location, leftover)
                        }
                    }
                }
            }
        }
    }

    private fun processMine(player: Player, hideout: HideoutPlayerData) {
        val mineLevel = hideout.getModuleLevel("mine")
        if (mineLevel <= 0) return
        val cfg = HideoutConfig.getModule("mine")?.getLevelConfig(mineLevel) ?: return
        if (cfg.productionRate <= 0) return

        // GPU加速: 每张显卡增加 50% 基础产率 (可配置)
        val gpuCount = hideout.minerGpuItems.size
        val gpuBoost = 1.0 + gpuCount * 0.5
        val effectiveRate = cfg.productionRate * gpuBoost

        val acc = hideout.productionAccumulators.getOrPut("mine") { 0.0 }
        hideout.productionAccumulators["mine"] = acc + effectiveRate / 20.0

        val current = hideout.productionAccumulators["mine"] ?: return
        if (current >= 1.0) {
            val whole = current.toInt()
            if (whole > 0) {
                hideout.productionAccumulators["mine"] = current - whole
                io.github.zzzyyylllty.chotentech.util.EconomyUtil.deposit(player, whole.toDouble())
                player.sendComponent("<yellow>矿场产出 <green>$whole <yellow>CitCoin")
            }
        }
    }

    fun startCrafting(player: Player, recipeId: String): Boolean {
        val data = player.getData() ?: return false
        val baseKey = getCurrentBaseKey(player)
        val hideout = data.hideoutData[baseKey] ?: return false
        if (!hideout.unlocked) return false

        val recipe = HideoutConfig.getRecipe(recipeId) ?: return false
        val moduleLevel = hideout.getModuleLevel(recipe.module)
        if (moduleLevel < recipe.minLevel) {
            player.sendComponent("<red>${HideoutConfig.getModule(recipe.module)?.name ?: recipe.module} 等级不足！需要 ${recipe.module} Lv.${recipe.minLevel}")
            return false
        }

        if (!isGeneratorRunning(hideout)) {
            player.sendComponent("<red>发电机未运行，无法进行合成！")
            return false
        }

        if (hideout.craftingQueues.any { it.recipeId == recipeId }) {
            player.sendComponent("<red>该配方已在合成队列中！")
            return false
        }

        for (req in recipe.inputs) {
            if (!hasRequiredItem(player, req)) {
                player.sendComponent("<red>缺少材料: ${req.sertralineId?.let { ItemNameHelper.getDisplayName(it) } ?: req.material} x${req.amount}")
                return false
            }
        }

        for (req in recipe.inputs) {
            removeRequiredItem(player, req)
        }

        val baseTime = recipe.craftTime
        // 智力技能减少合成耗时（每级 -0.5%，最高 -50%）
        val reduction = try {
            (PlayerDataManager(player).getLevel("intelligence") ?: 0L) * 0.005
        } catch (_: Exception) { 0.0 }
        val craftTime = maxOf(1, (baseTime * (1.0 - reduction)).toInt())
        hideout.craftingQueues += CraftingQueueItem(
            recipeId = recipeId,
            startTime = System.currentTimeMillis(),
            craftTime = craftTime,
            playerName = player.name,
        )

        player.sendComponent("<green>开始合成: <yellow>${recipe.id} <gray>(需 ${craftTime} 秒)")
        return true
    }

    private fun hasRequiredItem(player: Player, req: ItemRequirement): Boolean {
        var count = 0
        for (item in player.inventory.contents) {
            if (item == null || item.type.isAir) continue
            if (matchesRequirement(item, req)) count += item.amount
            if (count >= req.amount) return true
        }
        return count >= req.amount
    }

    private fun removeRequiredItem(player: Player, req: ItemRequirement) {
        var remaining = req.amount
        for (i in player.inventory.contents.indices) {
            val item = player.inventory.contents[i] ?: continue
            if (item.type.isAir || !matchesRequirement(item, req)) continue

            val take = minOf(remaining, item.amount)
            item.amount -= take
            remaining -= take
            if (item.amount <= 0) player.inventory.setItem(i, null)
            if (remaining <= 0) break
        }
    }

    private fun matchesRequirement(item: ItemStack, req: ItemRequirement): Boolean {
        if (req.sertralineId != null) {
            val id = io.github.zzzyyylllty.sertraline.Sertraline.api().getId(item)
            return id == req.sertralineId
        }
        if (req.material != null) {
            return item.type.name.equals(req.material, ignoreCase = true)
        }
        return false
    }

    fun addFuelItem(player: Player, slot: Int): Boolean {
        val data = player.getData() ?: return false
        val baseKey = getCurrentBaseKey(player)
        val hideout = data.hideoutData[baseKey] ?: return false
        if (!hideout.isModuleBuilt("generator")) {
            player.sendComponent("<red>发电机未建造！")
            return false
        }

        val item = player.inventory.getItem(slot) ?: return false
        if (item.type.isAir) return false

        val fuel = HideoutConfig.getFuelTypeByItem(item)
        if (fuel == null) {
            player.sendComponent("<red>该物品不能作为燃料！")
            return false
        }

        val emptySlot = (0 until 5).firstOrNull { !hideout.fuelInventory.containsKey(it) }
        if (emptySlot == null) {
            player.sendComponent("<red>燃料槽已满！")
            return false
        }

        val cloned = item.clone()
        cloned.amount = 1
        ItemSerializer.serializeNbt(cloned)?.let { hideout.fuelInventory[emptySlot] = it }
        item.amount--
        if (item.amount <= 0) player.inventory.setItem(slot, null)

        player.sendComponent("<green>已添加燃料: ${fuel.id}")
        return true
    }

    fun collectMinerOutput(player: Player): Boolean {
        val data = player.getData() ?: return false
        val baseKey = getCurrentBaseKey(player)
        val hideout = data.hideoutData[baseKey] ?: return false
        val acc = hideout.productionAccumulators["mine"] ?: 0.0
        if (acc < 1.0) return false

        val whole = acc.toInt()
        if (whole > 0) {
            hideout.productionAccumulators["mine"] = acc - whole
            io.github.zzzyyylllty.chotentech.util.EconomyUtil.deposit(player, whole.toDouble())
            player.sendComponent("<green>从矿场提取了 <yellow>$whole <green>CitCoin")
        }
        return true
    }

    fun upgradeModule(player: Player, moduleId: String): Boolean {
        val data = player.getData() ?: return false
        val baseKey = getCurrentBaseKey(player)
        val hideout = data.hideoutData[baseKey] ?: return false
        if (!hideout.unlocked) return false

        // 检查是否已有同模块正在升级
        if (hideout.pendingUpgrades.any { it.moduleId == moduleId }) {
            player.sendComponent("<red>该模块正在升级中！")
            return false
        }

        val module = HideoutConfig.getModule(moduleId) ?: return false
        val currentLevel = hideout.getModuleLevel(moduleId)

        if (currentLevel >= module.maxLevel) {
            player.sendComponent("<red>${module.name} 已达最高等级！")
            return false
        }

        val nextLevel = currentLevel + 1
        val levelCfg = module.getLevelConfig(nextLevel) ?: return false

        // 检查物品材料
        for (cost in levelCfg.upgradeCost) {
            val req = ItemRequirement(cost.material, cost.sertralineId, cost.amount)
            if (!hasRequiredItem(player, req)) {
                player.sendComponent("<red>缺少升级材料: ${cost.sertralineId?.let { ItemNameHelper.getDisplayName(it) } ?: cost.material} x${cost.amount}")
                return false
            }
        }

        // 检查货币
        for (cc in levelCfg.currencyCost) {
            if (!io.github.zzzyyylllty.chotentech.util.CurrencyUtil.hasBalance(player, cc.currency, cc.amount)) {
                val bal = io.github.zzzyyylllty.chotentech.util.CurrencyUtil.getBalance(player, cc.currency)
                player.sendComponent("<red>缺少货币: ${cc.currency} x${"%.0f".format(cc.amount)} (当前: ${"%.0f".format(bal)})")
                return false
            }
        }

        // 扣除物品材料
        for (cost in levelCfg.upgradeCost) {
            val req = ItemRequirement(cost.material, cost.sertralineId, cost.amount)
            removeRequiredItem(player, req)
        }

        // 扣除货币
        for (cc in levelCfg.currencyCost) {
            io.github.zzzyyylllty.chotentech.util.CurrencyUtil.remove(player, cc.currency, cc.amount)
        }

        // 加入升级队列
        hideout.pendingUpgrades += PendingUpgrade(
            moduleId = moduleId,
            targetLevel = nextLevel,
            startTime = System.currentTimeMillis(),
            upgradeTime = levelCfg.upgradeTime,
        )

        if (levelCfg.upgradeTime > 0) {
            player.sendComponent("<green>${module.name} 升级已开始 (需 ${levelCfg.upgradeTime} 秒)，可离线等待完成")
        } else {
            // 无耗时直接完成
            applyUpgrade(player, hideout, moduleId, nextLevel)
        }
        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f)
        return true
    }

    private fun processPendingUpgrades(player: Player, hideout: HideoutPlayerData) {
        if (hideout.pendingUpgrades.isEmpty()) return

        val completed = hideout.pendingUpgrades.filter { it.isComplete }
        if (completed.isEmpty()) return

        hideout.pendingUpgrades.removeAll(completed.toSet())

        for (upgrade in completed) {
            applyUpgrade(player, hideout, upgrade.moduleId, upgrade.targetLevel)
        }
    }

    private fun applyUpgrade(player: Player, hideout: HideoutPlayerData, moduleId: String, targetLevel: Int) {
        val module = HideoutConfig.getModule(moduleId)
        hideout.moduleLevels[moduleId] = targetLevel

        if (moduleId == "generator") {
            val capacity = getFuelCapacity(hideout)
            if (hideout.fuelAmount > capacity) hideout.fuelAmount = capacity
        }

        player.sendComponent("<green>${module?.name ?: moduleId} 已升级到 Lv.$targetLevel！")
        player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f)
    }

    fun useBedroom(player: Player): Boolean {
        val data = player.getData() ?: return false
        val baseKey = getCurrentBaseKey(player)
        val hideout = data.hideoutData[baseKey] ?: return false
        val level = hideout.getModuleLevel("bedroom")
        if (level <= 0) {
            player.sendComponent("<red>卧室未建造！")
            return false
        }

        val cfg = HideoutConfig.getModule("bedroom")?.getLevelConfig(level) ?: return false
        val cooldown = 8 * 60 * 60 * 1000L // 8 hours
        val elapsed = System.currentTimeMillis() - hideout.bedroomLastUse
        if (elapsed < cooldown) {
            val remaining = (cooldown - elapsed) / 1000
            player.sendComponent("<red>卧室仍在休息中，剩余 ${remaining / 60} 分 ${remaining % 60} 秒")
            return false
        }

        hideout.bedroomLastUse = System.currentTimeMillis()
        // 给予生命恢复效果 (持续秒数 = buffDuration, 等级 = buffAmplifier)
        if (cfg.buffDuration > 0) {
            player.addPotionEffect(
                org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.REGENERATION,
                    cfg.buffDuration * 20,
                    cfg.buffAmplifier,
                    false, true
                )
            )
        }
        player.sendComponent("<green>你在卧室休息了一会，感觉恢复了精力！")
        return true
    }

    fun healAtMedicalStation(player: Player): Boolean {
        val data = player.getData() ?: return false
        val baseKey = getCurrentBaseKey(player)
        val hideout = data.hideoutData[baseKey] ?: return false
        val level = hideout.getModuleLevel("medical_station")
        if (level <= 0) {
            player.sendComponent("<red>医疗站未建造！")
            return false
        }

        val points = hideout.productionAccumulators["medical_station"] ?: 0.0
        if (points < 1.0) {
            player.sendComponent("<red>医疗点数不足 (当前: ${"%.1f".format(points)})")
            return false
        }

        hideout.productionAccumulators["medical_station"] = points - 1.0
        player.setHealth(player.health.coerceAtMost(player.maxHealth - 1) + 1.0)
        player.sendComponent("<green>消耗 1 医疗点数，恢复 1 生命值")
        return true
    }

    fun collectProduction(player: Player, moduleId: String): Boolean {
        val data = player.getData() ?: return false
        val baseKey = getCurrentBaseKey(player)
        val hideout = data.hideoutData[baseKey] ?: return false
        val level = hideout.getModuleLevel(moduleId)
        if (level <= 0) {
            player.sendComponent("<red>该模块未建造！")
            return false
        }

        val cfg = HideoutConfig.getModule(moduleId)?.getLevelConfig(level) ?: return false
        val acc = hideout.productionAccumulators[moduleId] ?: 0.0
        if (acc < 1.0 || cfg.productionItem == null) {
            player.sendComponent("<red>没有可提取的产出 (累积: ${"%.1f".format(acc)})")
            return false
        }

        val whole = acc.toInt()
        hideout.productionAccumulators[moduleId] = acc - whole
        val item = io.github.zzzyyylllty.sertraline.Sertraline.api().buildItem(cfg.productionItem, player, amount = whole)
        if (item != null) {
            val remaining = player.inventory.addItem(item)
            remaining.values.forEach { leftover -> player.world.dropItem(player.location, leftover) }
            player.sendComponent("<green>从 ${cfg.productionItem} 提取了 <yellow>$whole <green>个")
        }
        return true
    }

    @Awake(LifeCycle.DISABLE)
    fun cancelHideoutTimer() {
        hideoutTask?.cancel()
        hideoutTask = null
    }

    fun unlockHideout(player: Player, baseKey: String? = null): Boolean {
        val data = player.getData() ?: return false
        val key = baseKey ?: getCurrentBaseKey(player)
        val hideout = data.hideoutData.getOrPut(key) { HideoutPlayerData() }
        if (hideout.unlocked) {
            player.sendComponent("<red>该基地的个人基地已解锁！")
            return false
        }
        hideout.unlocked = true
        // 自动建造前置模块
        player.sendComponent("<gold>■■ <green>个人基地已解锁！<gold> ■■")
        player.sendComponent("<gray>前往基地总览建造模块并添加燃料开始运作。")
        return true
    }
}
