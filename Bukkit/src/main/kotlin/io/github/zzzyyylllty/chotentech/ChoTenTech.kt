package io.github.zzzyyylllty.chotentech

import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.ChunkProvince
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.DataManager.autoLoadChunk
import io.github.zzzyyylllty.chotentech.data.IDType
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.PlayerHUDData
import io.github.zzzyyylllty.chotentech.data.SharedNoclipSession
import io.github.zzzyyylllty.chotentech.data.PowerManager.onReload
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import io.github.zzzyyylllty.chotentech.punish.PunishManager
import io.github.zzzyyylllty.chotentech.security.SecurityManager
import io.github.zzzyyylllty.chotentech.security.SecurityListener
import io.github.zzzyyylllty.chotentech.feature.gun.item.GunItemProcessors
import io.github.zzzyyylllty.chotentech.feature.keydoor.KeyDoorManager
import io.github.zzzyyylllty.chotentech.feature.projectile.ProjectileManager
import io.github.zzzyyylllty.chotentech.feature.signpass.SignPassManager
import io.github.zzzyyylllty.chotentech.feature.steampipe.SteamPipeManager
import io.github.zzzyyylllty.chotentech.function.AchievementManager
import io.github.zzzyyylllty.chotentech.globalmarket.GlobalMarketManager
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import io.github.zzzyyylllty.chotentech.feature.EndManager
import io.github.zzzyyylllty.chotentech.function.RankManager
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutConfig
import io.github.zzzyyylllty.chotentech.function.hideout.ItemNameHelper
import io.github.zzzyyylllty.chotentech.station.StationManager
import io.github.zzzyyylllty.chotentech.data.backpack.BackpackConfig
import io.github.zzzyyylllty.chotentech.listener.BackpackListener
import io.github.zzzyyylllty.chotentech.data.cancelAllConsumableTasks
import io.github.zzzyyylllty.chotentech.data.loadMedkitConfig
import io.github.zzzyyylllty.chotentech.data.loadSurgeryConfig
import io.github.zzzyyylllty.chotentech.data.loadConsumableConfigs
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.load.loadAchievementFiles
import io.github.zzzyyylllty.chotentech.load.loadAccountTypes
import io.github.zzzyyylllty.chotentech.data.interfereConfigs
import io.github.zzzyyylllty.chotentech.data.cosmeticConfigs
import io.github.zzzyyylllty.chotentech.data.loadInterfereFiles
import io.github.zzzyyylllty.chotentech.data.loadCosmeticFiles
import io.github.zzzyyylllty.chotentech.listener.initCosmeticEffects
import io.github.zzzyyylllty.chotentech.load.loadLevelFiles
import io.github.zzzyyylllty.chotentech.load.loadPowerFiles
import io.github.zzzyyylllty.chotentech.load.tempPowers
import io.github.zzzyyylllty.chotentech.util.CurrencyUtil
import io.github.zzzyyylllty.chotentech.util.EconomyUtil
import io.github.zzzyyylllty.chotentech.util.NotificationQueue
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.data.LevelTemplate
import io.github.zzzyyylllty.chotentech.data.Regen
import io.github.zzzyyylllty.chotentech.data.RegenBlock
import io.github.zzzyyylllty.chotentech.data.RegenTemplate
import io.github.zzzyyylllty.chotentech.data.load.loadLevelFiles as loadQuetiapineLevelFiles
import io.github.zzzyyylllty.chotentech.data.load.loadRegenFiles
import io.github.zzzyyylllty.chotentech.listener.releaseRegenBlocks
import io.github.zzzyyylllty.chotentech.logger.*
import io.github.zzzyyylllty.chotentech.util.QuetiapineLocalDependencyHelper
import io.github.zzzyyylllty.chotentech.util.dependencies
import io.github.zzzyyylllty.chotentech.worldgeneration.Level0Generator
import io.github.zzzyyylllty.chotentech.worldgeneration.Level1Generator
import io.github.zzzyyylllty.sertraline.Sertraline
import net.momirealms.craftengine.core.world.ChunkPos
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.generator.ChunkGenerator
import taboolib.common.LifeCycle
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.env.RuntimeEnv
import taboolib.common.platform.Awake
import taboolib.common.platform.Plugin
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.kether.KetherShell
import taboolib.module.lang.Language
import taboolib.module.lang.event.PlayerSelectLocaleEvent
import taboolib.module.lang.event.SystemSelectLocaleEvent
import taboolib.module.ui.enableRawTitleInVanillaInventory
import taboolib.module.ui.isRawTitleInVanillaInventoryEnabled
import taboolib.platform.BukkitPlugin
import taboolib.platform.BukkitWorldGenerator
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.script.CompiledScript

@RuntimeDependencies(
    RuntimeDependency(
        value = "!com.google.code.gson:gson:2.10.1",
        relocate = ["!com.google.gson", "!com.example.library.gson"]
    ),
)
object ChoTenTech : Plugin(), BukkitWorldGenerator {


    @Config("config.yml")
    lateinit var config: Configuration

    @Config("account-types.yml")
    lateinit var accountTypes: Configuration

    @Config("music-durations.yml")
    lateinit var musicDurations: Configuration

    @Config("bases.yml")
    lateinit var baseConfig: Configuration

    @Config("currency.yml")
    lateinit var currencyConfig: Configuration

    @Config("filter.yml")
    lateinit var filterConfig: Configuration

    @Config("chat-settings.yml")
    lateinit var chatSettings: Configuration

    @Config("backpack.yml")
    lateinit var backpackConfig: Configuration

    val console by lazy { console() }
    val consoleSender by lazy { console.castSafely<CommandSender>()!! }
    val sdClassList: LinkedHashMap<String, SurvivalDifficulty> = linkedMapOf()
    val levelsList: LinkedHashMap<String, Level> = linkedMapOf()
    val accountTypesList: LinkedHashMap<String, IDType> = linkedMapOf()
    val playerRegions = ConcurrentHashMap<String, Set<String>>()
    val playerDataMap = ConcurrentHashMap<String, PlayerData>()
    val playerHudMap = ConcurrentHashMap<String, PlayerHUDData>()
    val loadedChunk = ConcurrentHashMap<io.github.zzzyyylllty.chotentech.data.ChunkPos, ChunkProvince>()
    val playerRandomNoclips = ConcurrentHashMap<String, Set<String>>()
    val restartRandomNoclips = ConcurrentHashMap<String, Set<String>>()
    val sharedNoclipSessions = ConcurrentHashMap<String, SharedNoclipSession>()
    val sessionUnlockedNoclips = ConcurrentHashMap<String, Boolean>()

    // Quetiapine shared data
    val blockRegenMap = ConcurrentHashMap<Location, Regen>()
    val regenTemplatesByBlock = ConcurrentHashMap<RegenBlock, MutableList<String>>()
    val regenTemplates = ConcurrentHashMap<String, RegenTemplate>()
    val levels = ConcurrentHashMap<String, LevelTemplate>()
    val ketherScriptCache by lazy { LinkedHashMap<String, KetherShell.Cache?>() }
    val jsScriptCache by lazy { LinkedHashMap<String, CompiledScript?>() }
    val overrideVanilla: String? by lazy { config.getString("options.level.override-vanilla") }

    val dateTimeFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
    var devMode = true

    override fun onEnable() {
        GunItemProcessors.register()

        enableRawTitleInVanillaInventory()

        info("Enable")
        Language.enableSimpleComponent = true

        // 注意：配置/等级文件的加载已延后到 onActive()（LifeCycle.ACTIVE）。
        // ENABLE 阶段（尤其在世界正在创建时）可能一个世界都还没加载好，
        // 而 levels 里的位置解析需要世界已经存在。

        // Initialize chat system
        io.github.zzzyyylllty.chotentech.chat.TabooFilter.reload(filterConfig)
        io.github.zzzyyylllty.chotentech.chat.ChannelManager.reload()
        io.github.zzzyyylllty.chotentech.chat.ChatPlayerDataManager

        // Initialize backpack system (merged ChoTenBackpack)
        BackpackListener
        BackpackListener.init()

        // Initialize profile system
        io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
        io.github.zzzyyylllty.chotentech.listener.ProfileListener

        // Initialize punishment system
        PunishManager.reloadConfig()

        // Initialize security system (auth/SafeMode)
        SecurityManager.reload()
        SecurityListener
        SecurityListener.startPeriodicOpCheck()

        // Initialize end/completion system
        EndManager.init()

        // Initialize game time system
        io.github.zzzyyylllty.chotentech.listener.GameTimeManager.start()

        // Initialize attribute system
        AttributeManager.registerBuiltinAttributes()
        AttributeManager.loadAttributeFiles()
        AttributeManager.loadBasicAttributes()

        // Initialize damage type system
        DamageTypeRegistry.init()

        // Initialize steam pipe system
        SteamPipeManager.startTask()

        // Initialize key door system
        KeyDoorManager.onServerStart()
        KeyDoorManager.startAutoCloseTask()

        // Initialize Area6 teleporter system
        io.github.zzzyyylllty.chotentech.function.area6.Area6Teleporter.startTask()

        // Initialize global market
        GlobalMarketManager.loadAll()

        // Initialize bleedout / downed system
        io.github.zzzyyylllty.chotentech.function.bleedout.BleedoutManager

        // Register drop lock command and listener
        io.github.zzzyyylllty.chotentech.function.DropCommand

        // Register LuckPerms profile context calculator
        io.github.zzzyyylllty.chotentech.hook.ProfileContextCalculator.register()

        // Initialize gun system P1 (射击闭环): 独立配置 + 1 tick 运行循环
        io.github.zzzyyylllty.chotentech.feature.gun.GunSettings.load()
        // ⚠️ 必须显式引用这些监听器对象：
        // taboolib 的 @SubscribeEvent 是**类被加载时**注册的，没有被任何地方引用到的 object
        // 永远不会注册 —— 现象就是「按 Q / 左右键完全没反应」，而 tick 循环（显式 start）照常跑，
        // 所以 HUD、耐久、NBT 看起来都正常，只有输入全死。
        io.github.zzzyyylllty.chotentech.feature.gun.GunListener
        io.github.zzzyyylllty.chotentech.feature.gun.GunMenuListener
        io.github.zzzyyylllty.chotentech.feature.gun.GunDamage
        io.github.zzzyyylllty.chotentech.feature.crossbow.FlyingArrowListener
        info(
            "[Gun] 输入监听已装载: GunListener(交互/丢弃/换手/切栏/攻击) " +
                "GunMenuListener(菜单事件) GunDamage(伤害) FlyingArrowListener(弩箭)"
        )
        io.github.zzzyyylllty.chotentech.feature.gun.GunPacketInput.register()
        // 瞄准缩放（FOV）：拦截 / 改写服务端 Abilities 包，没有 packetevents 时自动降级
        io.github.zzzyyylllty.chotentech.feature.gun.GunZoom.register()
        io.github.zzzyyylllty.chotentech.feature.gun.GunStateManager.start()
    }

    override fun onDisable() {
        info("Disable")
        // 枪械系统先停循环再清弹道：tick 任务里会推进子弹，先停避免关服期间继续跑
        io.github.zzzyyylllty.chotentech.feature.gun.GunStateManager.stop()
        io.github.zzzyyylllty.chotentech.feature.gun.GunZoom.shutdown()
        io.github.zzzyyylllty.chotentech.feature.gun.GunPacketInput.shutdown()
        io.github.zzzyyylllty.chotentech.feature.gun.GunBallistics.clear()
        // 枪械系统 P2：关掉所有还开着的枪械 / 弹匣菜单（close 会顺带结束压弹/退弹模式、
        // 停掉读条与批量循环），并清空读条登记表与拾取提示节流缓存。
        // 弹匣的实际拾取冷却不在这里：时间戳写在物品 NBT 上、用世界 tick 比较，天然自过期。
        io.github.zzzyyylllty.chotentech.feature.gun.GunMenuListener.shutdown()
        io.github.zzzyyylllty.chotentech.feature.gun.GunAction.cancelAll()
        io.github.zzzyyylllty.chotentech.feature.gun.GunListener.clearCaches()
        DamageTypeRegistry.shutdown()
        ProjectileManager.cleanupAll()
        SteamPipeManager.cleanup()
        KeyDoorManager.onDisable()
        SignPassManager.cleanup()
        DeathDropManager.removeAll()
        GlobalMarketManager.saveToDisk()
        GlobalMarketManager.cleanup()
        onReload()
        AchievementManager.unload()
        RankManager.unload()
        cancelAllConsumableTasks()
        NotificationQueue.clearAll()
        SecurityListener.stopPeriodicOpCheck()
        io.github.zzzyyylllty.chotentech.listener.GameTimeManager.stop()

        // Unregister LuckPerms profile context calculator
        io.github.zzzyyylllty.chotentech.hook.ProfileContextCalculator.unregister()

        // Save all player profile data before clearing maps
        io.github.zzzyyylllty.chotentech.data.DataManager.saveAllPlayerData()

        // Clear backpack open sessions (merged ChoTenBackpack)
        BackpackListener.disable()

        // Clear static maps to prevent classloader memory leaks
        playerDataMap.clear()
        playerHudMap.clear()
        loadedChunk.clear()
        playerRegions.clear()
        playerRandomNoclips.clear()
        restartRandomNoclips.clear()
        sharedNoclipSessions.clear()
        sessionUnlockedNoclips.clear()
        blockRegenMap.clear()
        regenTemplatesByBlock.clear()
        regenTemplates.clear()
        levels.clear()
        // 此前 onDisable 漏掉的五个静态容器：它们只在 reloadCustomConfig
        // （/tql reload）里被清，而热重载不一定走那条路径——直接
        // /tql unload → load 就会把这几块静态内存跨代带下去，
        // 既是泄漏，也可能让旧数据污染新会话。
        sdClassList.clear()
        levelsList.clear()
        accountTypesList.clear()
        ketherScriptCache.clear()
        jsScriptCache.clear()

        // ULS 客户端菜单的静态状态。
        //
        // 这四个 clear() 一直存在但**从来没有调用方**——插件被禁用时
        // 它们全部泄漏。这个服务器跑在 TabooLib 下、会热重载，
        // 所以「禁用」不是进程的终点：残留的 openMenus / 动作注册 /
        // 经典轨布局会跨过一次重载活下来，然后和新的那套状态打架，
        // 表现为「reload 之后某些玩家一直看到旧菜单」。
        //
        // 顺序有讲究：先让所有玩家从菜单里退出去（close 会走
        // 经典轨的回收路径），再清动作表，最后清玩家表。
        io.github.zzzyyylllty.chotentech.client.menu.ULSMenuManager.clear()
        io.github.zzzyyylllty.chotentech.client.menu.ULSMenuActionRegistry.clear()
        io.github.zzzyyylllty.chotentech.client.menu.ULSMenuClassicRail.clear()
        io.github.zzzyyylllty.chotentech.client.ULSClientManager.clear()

        // 枪械系统 P0（GunRegistry）只有静态配置缓存：纯 ConcurrentHashMap 的
        // 解析结果，随类加载器一起卸载即可，这里**不需要**显式清理，
        // 所以 GunRegistry 目前也刻意没有 clear() 方法。
        // 注意：一旦后续阶段加入按玩家维护的枪械运行时状态（弹匣内容索引、开火任务、
        // 后坐力/过热状态等），必须回到这里补上对应的清理，否则热重载会跨代泄漏。
        // （届时形如：io.github.zzzyyylllty.chotentech.feature.gun.GunRegistry.clear()）
    }

    override fun onActive() {
        // ACTIVE 阶段在服务器启动完成之后触发，此时世界已经加载好，
        // 所以在这里同步加载配置/等级文件，避免 ENABLE 阶段「没有任何世界」把加载打断。
        reloadCustomConfig(async = false)

        autoLoadChunk()
        loadInterfereFiles()
        loadCosmeticFiles()
        initCosmeticEffects()
    }

    @Awake(LifeCycle.INIT)
    fun initDependenciesInit() {
        solveDependencies(dependencies)
    }

    fun solveDependencies(dependencies: List<String>, useTaboo: Boolean = false) {
        for (name in dependencies) {
            try {
                infoS("Trying to load dependencies from file $name")
                val resource = ChoTenTech::class.java.classLoader.getResource("META-INF/dependencies/$name.json")
                if (resource == null) {
                    severeS("Resource META-INF/dependencies/$name.json not found!")
                    continue
                }
                if (useTaboo) RuntimeEnv.ENV_DEPENDENCY.loadFromLocalFile(resource) else QuetiapineLocalDependencyHelper().loadFromLocalFile(resource)
                infoS("Trying to load dependencies from file $name ... DONE.")
            } catch (e: Exception) {
                severeS("Trying to load dependencies from file $name FAILED.")
                severeS("Exception: $e")
                e.printStackTrace()
            }
        }
    }

    @SubscribeEvent
    fun lang(event: PlayerSelectLocaleEvent) {
        event.locale = config.getString("lang", "zh_CN")!!
    }

    @SubscribeEvent
    fun lang(event: SystemSelectLocaleEvent) {
        event.locale = config.getString("lang", "zh_CN")!!
    }

    fun reloadCustomConfig(async: Boolean = true) {
        submit(async) {
            releaseRegenBlocks()
            config.reload()
            devMode = config.getBoolean("debug",false)
            io.github.zzzyyylllty.chotentech.util.OggDuration.load(config)
            ketherScriptCache.clear()
            jsScriptCache.clear()
            blockRegenMap.clear()
            regenTemplates.clear()
            regenTemplatesByBlock.clear()
            levels.clear()

            loadRegenFiles()
            loadQuetiapineLevelFiles()

            baseConfig.reload()
            currencyConfig.reload()
            filterConfig.reload()
            chatSettings.reload()
            backpackConfig.reload()
            onReload()
            io.github.zzzyyylllty.chotentech.chat.TabooFilter.reload(filterConfig)
            io.github.zzzyyylllty.chotentech.chat.ChannelManager.reload()
            accountTypesList.clear()
            levelsList.clear()
            loadPowerFiles()
            loadSDClassFile()
            loadAccountTypes()
            loadLevelFiles()
            BaseManager.loadBases(baseConfig)
            val hideoutDir = File(getDataFolder(), "hideout")
            if (!hideoutDir.exists()) releaseResourceFolder("hideout")
            HideoutConfig.load(hideoutDir)
            val stationDir = File(getDataFolder(), "stations")
            if (!stationDir.exists()) releaseResourceFolder("stations")
            StationManager.loadAll()
            val subscriptionDir = File(getDataFolder(), "subscriptions")
            if (!subscriptionDir.exists()) releaseResourceFolder("subscriptions")
            io.github.zzzyyylllty.chotentech.function.subscription.SubscriptionManager.loadAll()
            ItemNameHelper.load()
            CurrencyUtil.load(currencyConfig)
            RankManager.load()
            PunishManager.reloadConfig()
            SecurityManager.reload()
            EndManager.init()
            interfereConfigs.clear()
            cosmeticConfigs.clear()
            loadInterfereFiles()
            loadCosmeticFiles()
            reloadAchievements()
            ProjectileManager.loadProjectileFiles()
            // 旧弩/弹匣体系（arrows/ mags/ crossbows/ 三套 YAML）已在 P2 整体删除：
            // 弩改走 Sertraline 的 `gun` 物品 + GunRegistry / GunAction，不再有独立配置目录需要加载。
            // 示例定义属于 ChoTenTech 自己的资源；通过 Sertraline 的公开单文件解析 API
            // 触发原生物品处理器与所有 ItemLoadEvent 扩展，再重建强类型枪械快照。
            io.github.zzzyyylllty.chotentech.feature.gun.GunExampleItems.load()
            io.github.zzzyyylllty.chotentech.feature.gun.GunRegistry.reload()
            // 处理器可能在 Sertraline 重载时被清空，这里再确认一次（幂等）
            GunItemProcessors.register()
            // 枪械机制配置（独立文件 gun-mechanism.yml）跟着一起重载
            io.github.zzzyyylllty.chotentech.feature.gun.GunSettings.load()
            SteamPipeManager.loadConfig()
            KeyDoorManager.loadConfig()
            io.github.zzzyyylllty.chotentech.feature.keydoor.KeyDoorSertralineManager.loadConfig()
            SignPassManager.loadConfig()
            io.github.zzzyyylllty.chotentech.feature.trap.TrapManager.loadConfig()
            loadMedkitConfig()
            loadSurgeryConfig()
            loadConsumableConfigs()
            io.github.zzzyyylllty.chotentech.shop.ShopManager.loadAll()
            io.github.zzzyyylllty.chotentech.shop.GiftPackManager.loadAll()
            io.github.zzzyyylllty.chotentech.shop.ShopMenu.loadItemOutputNames()
            BackpackConfig.load()
        }
    }

    fun reloadAchievements() {
        loadAchievementFiles()
    }

    override fun getDefaultWorldGenerator(worldName: String?, name: String?): ChunkGenerator? {
        return when (worldName) {
            "level0inf" -> Level0Generator()
            "level1inf" -> Level1Generator()
            else -> null
        }
    }
}


/** 安全世界:在这些世界中玩家无敌且不消耗饱食度/精神/压力/体力等 */
fun isSafeWorld(player: Player): Boolean =
    ChoTenTech.config.getStringList("safe-worlds").any { it.equals(player.world.name, ignoreCase = true) }


fun loadSDClassFile() {

    val config = YamlConfiguration.loadConfiguration(File(getDataFolder(), "sdclass.yml"))

    // 每次加载前先清空：sdClassList 是全局静态 LinkedHashMap，
    // 此前 loadSDClass 只 put 不 clear，多次 reload 时旧条目会
    // 累积且不会随配置删除而消失（陈旧数据污染 + 内存增长）。
    sdClassList.clear()

    for (iroot in config.getKeys(false)) {
        config.getConfigurationSection(iroot)?.let { loadSDClass(iroot,it) }
    }
}

fun loadSDClass(root: String, config: ConfigurationSection) {

    sdClassList.put(root,SurvivalDifficulty(
        classname = config.getString("classname") ?: "Unknown",
        one = config.getString("one") ?: "Unknown",
        two = config.getString("two") ?: "Unknown",
        three = config.getString("three") ?: "Unknown",
        prefix = config.getString("prefix") ?: "Unknown",
    ))
}
