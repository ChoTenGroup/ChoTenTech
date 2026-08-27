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
import io.github.zzzyyylllty.chotentech.feature.keydoor.KeyDoorManager
import io.github.zzzyyylllty.chotentech.feature.crossbow.CrossbowManager
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
import io.github.zzzyyylllty.chotentech.data.loadConsumableConfigs
import io.github.zzzyyylllty.chotentech.damage.DamageTypeRegistry
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.load.loadAchievementFiles
import io.github.zzzyyylllty.chotentech.load.loadAccountTypes
import io.github.zzzyyylllty.chotentech.data.conflictConfigs
import io.github.zzzyyylllty.chotentech.data.cosmeticConfigs
import io.github.zzzyyylllty.chotentech.data.loadConflictFiles
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

        enableRawTitleInVanillaInventory()

        info("Enable")
        Language.enableSimpleComponent = true

        reloadCustomConfig()

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
    }

    override fun onDisable() {
        info("Disable")
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
    }

    override fun onActive() {
        autoLoadChunk()
        loadConflictFiles()
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
            ItemNameHelper.load()
            CurrencyUtil.load(currencyConfig)
            RankManager.load()
            PunishManager.reloadConfig()
            SecurityManager.reload()
            EndManager.init()
            conflictConfigs.clear()
            cosmeticConfigs.clear()
            loadConflictFiles()
            loadCosmeticFiles()
            reloadAchievements()
            ProjectileManager.loadProjectileFiles()
            CrossbowManager.loadArrowConfigs()
            CrossbowManager.loadMagazineConfigs()
            CrossbowManager.loadCrossbowConfigs()
            SteamPipeManager.loadConfig()
            KeyDoorManager.loadConfig()
            io.github.zzzyyylllty.chotentech.feature.keydoor.KeyDoorSertralineManager.loadConfig()
            SignPassManager.loadConfig()
            io.github.zzzyyylllty.chotentech.feature.trap.TrapManager.loadConfig()
            loadMedkitConfig()
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

    override fun getDefaultWorldGenerator(
        p0: String?,
        p1: String?
    ): ChunkGenerator? {
        TODO("Not yet implemented")
    }


}


/** 安全世界:在这些世界中玩家无敌且不消耗饱食度/精神/压力/体力等 */
fun isSafeWorld(player: Player): Boolean =
    ChoTenTech.config.getStringList("safe-worlds").any { it.equals(player.world.name, ignoreCase = true) }


fun loadSDClassFile() {

    val config = YamlConfiguration.loadConfiguration(File(getDataFolder(), "sdclass.yml"))

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
