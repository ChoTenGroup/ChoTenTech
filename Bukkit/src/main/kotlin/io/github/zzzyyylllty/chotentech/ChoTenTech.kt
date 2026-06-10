

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
import io.github.zzzyyylllty.chotentech.function.AchievementManager
import io.github.zzzyyylllty.chotentech.function.BaseManager
import io.github.zzzyyylllty.chotentech.function.DeathDropManager
import io.github.zzzyyylllty.chotentech.function.RankManager
import io.github.zzzyyylllty.chotentech.function.hideout.HideoutConfig
import io.github.zzzyyylllty.chotentech.attribute.manager.AttributeManager
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
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.Sertraline
import net.momirealms.craftengine.core.world.ChunkPos
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.generator.ChunkGenerator
import taboolib.common.LifeCycle
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Awake
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.database.getHost
import taboolib.module.lang.Language
import taboolib.module.ui.enableRawTitleInVanillaInventory
import taboolib.module.ui.isRawTitleInVanillaInventoryEnabled
import taboolib.platform.BukkitPlugin
import taboolib.platform.BukkitWorldGenerator
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

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

    @Config("bases.yml")
    lateinit var baseConfig: Configuration

    @Config("hideout.yml")
    lateinit var hideoutConfig: Configuration

    @Config("currency.yml")
    lateinit var currencyConfig: Configuration

    val console by lazy { console() }
    val consoleSender by lazy { console.castSafely<CommandSender>()!! }
    val host by lazy { config.getHost("database") }
    val dataSource by lazy { host.createDataSource() }
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

    val dateTimeFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
    var devMode = true

    override fun onEnable() {

        enableRawTitleInVanillaInventory()

        info("Enable")
        Language.enableSimpleComponent = true
        EconomyUtil.setup()
        reloadCustomConfig()

        // Initialize punishment system
        PunishManager.reloadConfig()

        // Initialize attribute system
        AttributeManager.registerBuiltinAttributes()
        AttributeManager.loadAttributeFiles()
        AttributeManager.loadBasicAttributes()
//        val worlds = Bukkit.getWorlds()
//        submitAsync {
//            worlds.forEach { world ->
//                devLog("开始加载世界 ${world.name}")
//                world.loadedChunks.forEach { chunk ->
//                    DataManager.loadChunkData(chunk)
//                }
//                devLog("开始加载世界 ${world.name} 完成。")
//            }
//            devLog("区块加载完成。")
//        }

    }

    override fun onDisable() {
        info("Disable")
        DeathDropManager.removeAll()
        onReload()
        AchievementManager.unload()
        RankManager.unload()
    }

    override fun onActive() {
        autoLoadChunk()
        loadConflictFiles()
        loadCosmeticFiles()
        initCosmeticEffects()
    }
    /*
    fun compat() {
        if (Bukkit.getPluginManager().getPlugin("Chemdah") != null) {
            connectChemdah()
        }
    }*/

    fun reloadCustomConfig(async: Boolean = true) {
        submit(async) {

            config.reload()
            baseConfig.reload()
            hideoutConfig.reload()
            currencyConfig.reload()
            onReload()
            devMode = config.getBoolean("debug",false)
            accountTypesList.clear()
            levelsList.clear()
            loadPowerFiles()
            loadSDClassFile()
            loadAccountTypes()
            loadLevelFiles()
            BaseManager.loadBases(baseConfig)
            HideoutConfig.load(hideoutConfig)
            CurrencyUtil.load(currencyConfig)
            RankManager.load()
            PunishManager.reloadConfig()
            conflictConfigs.clear()
            cosmeticConfigs.clear()
            loadConflictFiles()
            loadCosmeticFiles()
            reloadAchievements()
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