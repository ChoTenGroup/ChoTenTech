package io.github.zzzyyylllty.chotentech.function

import io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap
import io.github.zzzyyylllty.chotentech.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Trident
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import taboolib.common.platform.event.EventPriority
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.BrewEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.util.Vector
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

/**
 * 副等级系统管理器
 *
 * 管理 14 个副等级的经验获取与事件监听：
 * 耐力 / 健康 / 力量 / 智力 / 敏捷 / 投掷 / 理疗 / 采矿 / 开锁
 */
object SkillManager {

    // ==================== 可配置经验值 ====================
    // 采矿：material_name -> [min_exp, max_exp]
    internal val miningXp = ConcurrentHashMap<String, DoubleArray>()

    // 简单数值配置（配置文件加载）
    internal var strengthDamageMultiplier = 2.0
    internal var strengthMaxPerHit = 20.0
    internal var enduranceDamageMultiplier = 1.0
    internal var enduranceMaxPerEvent = 50.0
    private var enduranceSprintExp = 0.2
    private var enduranceSwimExp = 0.3
    internal var healthHealMultiplier = 2.0
    internal var healthMaxPerEvent = 10.0
    internal var agilityMoveExp = 0.1
    internal var agilityJumpExp = 1.0
    internal var agilityFallExp = 2.0
    private var lockpickingSuccessMin = 15.0
    private var lockpickingSuccessMax = 30.0
    private var lockpickingFailMin = 2.0
    private var lockpickingFailMax = 5.0

    // 钓鱼战利品回退经验（无loot表匹配时使用）
    internal var fishingFallbackXpMin = 3.0
    internal var fishingFallbackXpMax = 8.0

    // 智力配置
    internal val intelligenceXp = ConcurrentHashMap<String, DoubleArray>()

    // 投掷配置
    internal val throwingXp = ConcurrentHashMap<String, DoubleArray>()

    // 理疗配置
    internal val physiotherapyXp = ConcurrentHashMap<String, DoubleArray>()

    // 钓鱼配置
    internal val fishingXp = ConcurrentHashMap<String, DoubleArray>()

    // 钓鱼战利品表：最低等级 -> 物品列表
    internal val fishingLootTables = ConcurrentHashMap<Int, List<FishingLootEntry>>()

    // ==================== 状态追踪 ====================
    private val sprintingPlayers = ConcurrentHashMap<UUID, Boolean>()
    private var moveTask: PlatformExecutor.PlatformTask? = null
    internal val lastJumpY = ConcurrentHashMap<UUID, Double>()
    private val lastFallLocation = ConcurrentHashMap<UUID, Double>()

    // 已知医疗物品 ID 前缀列表（用于理疗经验检测）
    private val medicalItemPrefixes = listOf(
        "bandage", "medkit", "pill", "pills", "vaseline",
        "antibiotic", "zaped", "splint", "surgical"
    )

    // ==================== 初始化 ====================

    /** 释放技能等级配置文件和经验配置文件 */
    @Awake(LifeCycle.INIT)
    fun releaseConfigs() {
        val skillLevelFiles = listOf(
            "level/endurance.yml", "level/health.yml", "level/strength.yml",
            "level/intelligence.yml", "level/agility.yml", "level/throwing.yml",
            "level/physiotherapy.yml", "level/mining.yml",
            "level/lockpicking.yml",
            "level/farming.yml", "level/fishing.yml", "level/noclipping.yml",
            "level/woodcutting.yml"
        )
        for (file in skillLevelFiles) {
            try {
                releaseResourceFile(file)
            } catch (_: Exception) { /* 已存在则跳过 */ }
        }
        // 释放经验配置文件
        if (!File(getDataFolder(), "skill-xp.yml").exists()) {
            try {
                releaseResourceFile("skill-xp.yml")
            } catch (_: Exception) { /* ignore */ }
        }
        // 释放钓鱼战利品表
        if (!File(getDataFolder(), "fishing/loot.yml").exists()) {
            try {
                releaseResourceFile("fishing/loot.yml")
            } catch (_: Exception) { /* ignore */ }
        }
    }

    /** 加载经验配置 */
    @Awake(LifeCycle.ACTIVE)
    fun loadXpConfig() {
        miningXp.clear()
        intelligenceXp.clear()
        throwingXp.clear()
        physiotherapyXp.clear()
        fishingXp.clear()

        val file = File(getDataFolder(), "skill-xp.yml")
        if (!file.exists()) {
            devLog("[SkillManager] skill-xp.yml 不存在，使用默认值")
            return
        }

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)

        // 采矿
        val miningSection = config.getConfigurationSection("mining.break")
        if (miningSection != null) {
            for (key in miningSection.getKeys(false)) {
                parseRange(miningSection.getString(key, "")!!)?.let { miningXp[key] = it }
            }
        }

        // 力量
        strengthDamageMultiplier = config.getDouble("strength.damage_multiplier", 2.0)
        strengthMaxPerHit = config.getDouble("strength.max_per_hit", 20.0)

        // 耐力
        enduranceDamageMultiplier = config.getDouble("endurance.damage_taken_multiplier", 1.0)
        enduranceMaxPerEvent = config.getDouble("endurance.max_per_event", 50.0)
        enduranceSprintExp = config.getDouble("endurance.sprint_exp_per_tick", 0.2)
        enduranceSwimExp = config.getDouble("endurance.swim_exp_per_tick", 0.3)

        // 健康
        healthHealMultiplier = config.getDouble("health.heal_multiplier", 2.0)
        healthMaxPerEvent = config.getDouble("health.max_per_event", 10.0)

        // 智力
        val intSection = config.getConfigurationSection("intelligence")
        if (intSection != null) {
            for (key in intSection.getKeys(false)) {
                parseRange(intSection.getString(key, "")!!)?.let { intelligenceXp[key] = it }
            }
        }

        // 敏捷
        agilityMoveExp = config.getDouble("agility.move_exp_per_tick", 0.1)
        agilityJumpExp = config.getDouble("agility.jump_exp", 1.0)
        agilityFallExp = config.getDouble("agility.safe_fall_exp_per_block", 2.0)

        // 投掷
        val throwSection = config.getConfigurationSection("throwing")
        if (throwSection != null) {
            for (key in throwSection.getKeys(false)) {
                parseRange(throwSection.getString(key, "")!!)?.let { throwingXp[key] = it }
            }
        }

        // 理疗
        val physioSection = config.getConfigurationSection("physiotherapy")
        if (physioSection != null) {
            for (key in physioSection.getKeys(false)) {
                parseRange(physioSection.getString(key, "")!!)?.let { physiotherapyXp[key] = it }
            }
        }

        // 钓鱼
        val fishingSection = config.getConfigurationSection("fishing")
        if (fishingSection != null) {
            for (key in fishingSection.getKeys(false)) {
                parseRange(fishingSection.getString(key, "")!!)?.let { fishingXp[key] = it }
            }
        }

        // 钓鱼战利品表（从 fishing-loot.yml 加载）
        loadFishingLootTable()

        // 开锁
        val lockSection = config.getConfigurationSection("lockpicking")
        if (lockSection != null) {
            val success = parseRange(lockSection.getString("unlock_success", "15-30")!!)
            if (success != null) { lockpickingSuccessMin = success[0]; lockpickingSuccessMax = success[1] }
            val fail = parseRange(lockSection.getString("unlock_fail", "2-5")!!)
            if (fail != null) { lockpickingFailMin = fail[0]; lockpickingFailMax = fail[1] }
        }

        devLog("[SkillManager] 副等级经验配置已加载: 采矿${miningXp.size}")
    }

    /** 启动周期性移动经验任务（耐力/敏捷/投掷） */
    @Awake(LifeCycle.ACTIVE)
    fun startMoveTask() {
        moveTask = submit(period = 20) { // 每秒一次
            for (player in Bukkit.getOnlinePlayers()) {
                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
                if (playerDataMap[player.uniqueId.toString()] == null) continue

                if (player.isSprinting) {
                    val xp = if (player.isSwimming()) enduranceSwimExp else enduranceSprintExp
                    if (xp > 0) giveSkillXp(player, "endurance", xp)
                }

                // 敏捷：行走/潜行经验
                if (!player.isSprinting && (player.isSneaking() || !player.location.block.isLiquid)) {
                    if (agilityMoveExp > 0) giveSkillXp(player, "agility", agilityMoveExp)
                }
            }
        }
    }

    @Awake(LifeCycle.DISABLE)
    fun stopMoveTask() {
        moveTask?.cancel()
        moveTask = null
        sprintingPlayers.clear()
        lastJumpY.clear()
        lastFallLocation.clear()
    }

    // ==================== 公共 API ====================

    /** 给玩家添加副等级经验（自动检查升级） */
    fun giveSkillXp(player: Player, skill: String, xp: Double) {
        if (xp <= 0) return
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
        try {
            val mgr = PlayerDataManager(player)
            mgr.addExp(skill, xp)
            // checkUpdateLevel 由 QuetiapineAddExpEvent 监听器自动触发
        } catch (_: Exception) { /* 数据尚未就绪 */ }
    }

    /** 给理疗经验（由 HealingManager.enter 调用，替代 PlayerItemConsumeEvent 监听） */
    fun givePhysiotherapyXp(player: Player, item: ItemStack) {
        val xp = getPhysiotherapyXpForItem(item) ?: getPhysiotherapyXpBySertralineId(item)
        if (xp != null && xp > 0) {
            giveSkillXp(player, "physiotherapy", xp)
        }
    }

    // ==================== 钓鱼战利品 ====================

    /** 从 fishing/loot.yml 加载战利品表 */
    internal fun loadFishingLootTable() {
        fishingLootTables.clear()
        val file = File(getDataFolder(), "fishing/loot.yml")
        if (!file.exists()) {
            devLog("[SkillManager] fishing/loot.yml 不存在，跳过战利品表加载")
            return
        }
        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
        for (levelKey in config.getKeys(false)) {
            val minLevel = levelKey.toIntOrNull() ?: continue
            val itemMaps = config.getMapList("$levelKey.items")
            if (itemMaps.isEmpty()) continue
            val entries = mutableListOf<FishingLootEntry>()
            for (map in itemMaps) {
                val id = map["id"]?.toString() ?: continue
                val weight = (map["weight"] as? Number)?.toInt() ?: continue
                val xpStr = map["xp"]?.toString() ?: continue
                val xpRange = parseRange(xpStr) ?: continue
                val amountStr = map["amount"]?.toString() ?: "1"
                val amountParts = amountStr.split("-")
                val amountMin = amountParts[0].toIntOrNull() ?: 1
                val amountMax = amountParts.getOrNull(1)?.toIntOrNull() ?: amountMin
                entries.add(FishingLootEntry(id, weight, xpRange, amountMin..amountMax))
            }
            if (entries.isNotEmpty()) {
                fishingLootTables[minLevel] = entries
            }
        }
        devLog("[SkillManager] 钓鱼战利品表已加载: ${fishingLootTables.size} 个等级段")
    }

    /** 根据玩家钓鱼等级从战利品表中选取物品 */
    internal fun selectFishingLoot(player: Player): Pair<String, Double>? {
        val level = try { PlayerDataManager(player).getLevel("fishing") ?: 0L } catch (_: Exception) { 0L }

        // 找到匹配的最高等级段（<= 玩家等级）
        val matchedLevel = fishingLootTables.keys
            .filter { it <= level }
            .maxOrNull() ?: return null

        val entries = fishingLootTables[matchedLevel] ?: return null
        if (entries.isEmpty()) return null

        // 加权随机选择
        val totalWeight = entries.sumOf { it.weight }
        if (totalWeight <= 0) return null
        var roll = ThreadLocalRandom.current().nextInt(totalWeight)
        for (entry in entries) {
            roll -= entry.weight
            if (roll < 0) {
                val xp = randomXp(entry.xpRange)
                return Pair(entry.id, xp)
            }
        }
        return null
    }

    /** 获取随机经验值 [min, max] */
    internal fun randomXp(range: DoubleArray): Double {
        if (range.size < 2) return range[0]
        return range[0] + ThreadLocalRandom.current().nextDouble() * (range[1] - range[0])
    }

    /** 解析 "5-10" 格式为 double 数组 */
    private fun parseRange(str: String): DoubleArray? {
        val parts = str.split("-")
        if (parts.size != 2) return null
        val min = parts[0].trim().toDoubleOrNull() ?: return null
        val max = parts[1].trim().toDoubleOrNull() ?: return null
        return doubleArrayOf(min, max)
    }

    // ==================== 开锁回调（由 KeyDoorManager 调用） ====================

    fun onDoorUnlocked(player: Player) {
        val xp = lockpickingSuccessMin + ThreadLocalRandom.current().nextDouble() * (lockpickingSuccessMax - lockpickingSuccessMin)
        giveSkillXp(player, "lockpicking", xp)
    }

    fun onDoorUnlockFailed(player: Player) {
        val xp = lockpickingFailMin + ThreadLocalRandom.current().nextDouble() * (lockpickingFailMax - lockpickingFailMin)
        giveSkillXp(player, "lockpicking", xp)
    }
}

// ============================================================================
// 事件监听器
// ============================================================================

/** 钓鱼战利品条目 */
data class FishingLootEntry(
    val id: String,
    val weight: Int,
    val xpRange: DoubleArray,
    val amountRange: IntRange
)

/** 采矿经验 — 破坏方块 */
@SubscribeEvent
fun onBlockBreakForMining(event: BlockBreakEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    val material = event.block.type
    val range = SkillManager.miningXp[material.name]
    if (range != null) {
        val xp = SkillManager.randomXp(range)
        SkillManager.giveSkillXp(player, "mining", xp)
    }
}

/** 力量经验 — 近战伤害（MONITOR：事件最终态，忽略已取消事件） */
@SubscribeEvent(EventPriority.MONITOR, ignoreCancelled = true)
fun onEntityDamageForStrength(event: EntityDamageByEntityEvent) {
    val player = event.damager as? Player ?: return
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    if (event.damager is Projectile) return
    val damage = event.finalDamage
    if (damage <= 5) return
    var xp = (damage * SkillManager.strengthDamageMultiplier).coerceAtMost(SkillManager.strengthMaxPerHit)
    // 暴击额外 50% 经验
    if (player.fallDistance > 0.0f) {
        xp *= 1.5
    }
    if (xp > 0) SkillManager.giveSkillXp(player, "strength", xp)
}

/** 耐力经验 — 承受伤害 */
@SubscribeEvent
fun onEntityDamageForEndurance(event: EntityDamageEvent) {
    val player = event.entity as? Player ?: return
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    val damage = event.finalDamage
    if (damage <= 0) return
    // 致命伤不获得经验
    if (damage >= player.health) return
    val xp = (damage * SkillManager.enduranceDamageMultiplier)
        .coerceAtMost(SkillManager.enduranceMaxPerEvent)
    SkillManager.giveSkillXp(player, "endurance", xp)
}

/** 健康经验 — 生命恢复 */
@SubscribeEvent
fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
    val player = event.entity as? Player ?: return
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    val amount = event.amount
    if (amount <= 0) return
    val xp = (amount * SkillManager.healthHealMultiplier).coerceAtMost(SkillManager.healthMaxPerEvent)
    if (xp > 0) SkillManager.giveSkillXp(player, "health", xp)
}

/** 智力经验 — 附魔 */
@SubscribeEvent
fun onEnchantForIntelligence(event: EnchantItemEvent) {
    val player = event.enchanter
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    val range = SkillManager.intelligenceXp["enchant"]
    if (range != null) {
        SkillManager.giveSkillXp(player, "intelligence", SkillManager.randomXp(range))
    }
}

/** 智力经验 — 合成 */
@SubscribeEvent
fun onCraftForIntelligence(event: CraftItemEvent) {
    val player = event.whoClicked as? Player ?: return
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    val range = SkillManager.intelligenceXp["craft"]
    if (range != null) {
        SkillManager.giveSkillXp(player, "intelligence", SkillManager.randomXp(range))
    }
}

/** 智力经验 — 酿造 */
@SubscribeEvent
fun onBrewForIntelligence(event: BrewEvent) {
    // 酿造完成后给所有附近玩家加经验
    val block = event.block
    val location = block.location
    val nearbyPlayers = location.getNearbyPlayers(5.0).filter { p ->
        p.gameMode != GameMode.CREATIVE && p.gameMode != GameMode.SPECTATOR
    }
    val range = SkillManager.intelligenceXp["brew"]
    if (range != null) {
        val xp = SkillManager.randomXp(range)
        for (player in nearbyPlayers) {
            SkillManager.giveSkillXp(player, "intelligence", xp)
        }
    }
}

/** 投掷经验 — 投射物命中 */
@SubscribeEvent
fun onProjectileHitForThrowing(event: ProjectileHitEvent) {
    val shooter = event.entity.shooter as? Player ?: return
    if (shooter.gameMode == GameMode.CREATIVE || shooter.gameMode == GameMode.SPECTATOR) return
    val projectile = event.entity
    val typeKey = when {
        projectile is Trident -> "trident_hit"
        projectile.type.name.contains("SNOWBALL") -> "snowball_hit"
        projectile.type.name.contains("EGG") -> "egg_hit"
        projectile.type.name.contains("SPLASH_POTION") -> "splash_potion_hit"
        projectile.type.name.contains("LINGERING_POTION") -> "lingering_potion_hit"
        projectile.type.name.contains("EXPERIENCE_BOTTLE") -> "experience_bottle"
        projectile.type.name.contains("FIREWORK") -> "firework"
        projectile.type.name.contains("FISHING") -> "fishing_rod"
        else -> return
    }
    val range = SkillManager.throwingXp[typeKey] ?: return
    SkillManager.giveSkillXp(shooter, "throwing", SkillManager.randomXp(range))
}

/** 投掷准度 — 根据投掷等级减少投射物偏差 */
@SubscribeEvent
fun onProjectileLaunchForAccuracy(event: ProjectileLaunchEvent) {
    val projectile = event.entity
    val shooter = projectile.shooter as? Player ?: return
    if (shooter.gameMode == GameMode.CREATIVE || shooter.gameMode == GameMode.SPECTATOR) return

    val level = try { PlayerDataManager(shooter).getLevel("throwing") ?: 0L } catch (_: Exception) { 0L }
    // level 0 = 最大偏差, level 100 = 无偏差
    val spread = 0.3 * (1.0 - level / 100.0)
    if (spread <= 0.0) return

    val velocity = projectile.velocity
    val speed = velocity.length()
    if (speed < 0.01) return

    val random = ThreadLocalRandom.current()
    projectile.velocity = velocity.clone().add(Vector(
        (random.nextDouble() - 0.5) * spread,
        (random.nextDouble() - 0.5) * spread * 0.5,
        (random.nextDouble() - 0.5) * spread
    ))
}

/** 钓鱼经验 — 钓到鱼/实体，使用战利品表覆盖掉落 */
@SubscribeEvent
fun onFishForFishing(event: PlayerFishEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    if (event.state != PlayerFishEvent.State.CAUGHT_FISH && event.state != PlayerFishEvent.State.CAUGHT_ENTITY) return

    // 从战利品表选取物品
    val loot = SkillManager.selectFishingLoot(player)
    if (loot != null) {
        val (itemId, xp) = loot

        // 覆盖掉落物
        val caught = event.caught
        if (caught is Item) {
            val amount = getRandomAmount(itemId)
            val newItem = buildFishingItem(itemId, player, amount)
            if (newItem != null) {
                caught.itemStack = newItem
            }
        }

        // 给经验
        if (xp > 0) SkillManager.giveSkillXp(player, "fishing", xp)
    } else {
        // 无战利品表匹配，给保底经验
        val range = doubleArrayOf(SkillManager.fishingFallbackXpMin, SkillManager.fishingFallbackXpMax)
        SkillManager.giveSkillXp(player, "fishing", SkillManager.randomXp(range))
    }
}

/** 从物品ID获取随机数量 */
private fun getRandomAmount(itemId: String): Int {
    for (entries in SkillManager.fishingLootTables.values) {
        val entry = entries.find { it.id == itemId } ?: continue
        val range = entry.amountRange
        return if (range.first == range.last) range.first
        else ThreadLocalRandom.current().nextInt(range.first, range.last + 1)
    }
    return 1
}

/** 根据物品ID构建 ItemStack（支持 Sertraline ID 和 MINECRAFT_xxx） */
private fun buildFishingItem(itemId: String, player: Player, amount: Int): org.bukkit.inventory.ItemStack? {
    return if (itemId.startsWith("MINECRAFT_")) {
        val material = try { Material.valueOf(itemId.removePrefix("MINECRAFT_")) } catch (_: Exception) { null }
        material?.let { org.bukkit.inventory.ItemStack(it, amount) }
    } else {
        try {
            val item = io.github.zzzyyylllty.sertraline.Sertraline.api().buildItem(itemId, player, amount = amount)
            if (item != null && item.type != Material.AIR) item else null
        } catch (_: Exception) { null }
    }
}

/** 通过物品 Material 判断是否为理疗物品（原版药水/蘑菇煲等） */
private fun getPhysiotherapyXpForItem(item: ItemStack): Double? {
    val type = item.type
    // 原版药水
    if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
        val range = SkillManager.physiotherapyXp["pill_use"]
        return range?.let { SkillManager.randomXp(it) }
    }
    // 蜂蜜瓶
    if (type == Material.HONEY_BOTTLE) {
        return SkillManager.randomXp(SkillManager.physiotherapyXp["pill_use"] ?: doubleArrayOf(3.0, 8.0))
    }
    return null
}

/** 通过 Sertraline ID 判断是否为理疗物品 */
private fun getPhysiotherapyXpBySertralineId(item: ItemStack): Double? {
    val sertralineId = try { Sertraline.api().getId(item) } catch (_: Exception) { null } ?: return null
    val lower = sertralineId.lowercase()

    return when {
        lower.contains("bandage") -> {
            val range = SkillManager.physiotherapyXp["bandage_use"] ?: SkillManager.physiotherapyXp["sterile_bandage_use"]
            range?.let { SkillManager.randomXp(it) } ?: SkillManager.randomXp(doubleArrayOf(5.0, 10.0))
        }
        lower.contains("medkit") -> {
            val range = SkillManager.physiotherapyXp["medkit_use"]
            range?.let { SkillManager.randomXp(it) } ?: SkillManager.randomXp(doubleArrayOf(10.0, 25.0))
        }
        lower.contains("pill") || lower.contains("vaseline") || lower.contains("antibiotic") || lower.contains("zaped") || lower.contains("epsilon") || lower.contains("painkiller") || lower.contains("sql") -> {
            val range = SkillManager.physiotherapyXp["pill_use"]
            range?.let { SkillManager.randomXp(it) } ?: SkillManager.randomXp(doubleArrayOf(3.0, 8.0))
        }
        else -> null
    }
}

/** 敏捷经验 — 跳跃检测 */
@SubscribeEvent
fun onPlayerMoveForAgility(event: PlayerMoveEvent) {
    val player = event.player
    if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return
    if (event.isCancelled) return

    val from = event.from
    val to = event.to ?: return

    // 跳检测：在空中的玩家且上一个位置在地面
    val lastY = SkillManager.lastJumpY[player.uniqueId]
    if (!player.isOnGround && lastY != null && from.y > lastY + 3 && from.y == to.y) {
        // 玩家起跳
        SkillManager.giveSkillXp(player, "agility", SkillManager.agilityJumpExp)
    }
    SkillManager.lastJumpY[player.uniqueId] = to.y

    // 坠落检测：高处 → 安全落地（使用 Bukkit fallDistance）
    if (player.isOnGround && player.fallDistance >= 3.0) {
        val xp = min(50.0, player.fallDistance * SkillManager.agilityFallExp)
        if (xp > 0) SkillManager.giveSkillXp(player, "agility", xp)
    }
}
