

package io.github.zzzyyylllty.chotentech

import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.ChunkProvince
import io.github.zzzyyylllty.chotentech.data.DataManager
import io.github.zzzyyylllty.chotentech.data.DataManager.autoLoadChunk
import io.github.zzzyyylllty.chotentech.data.IDType
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.PlayerHUDData
import io.github.zzzyyylllty.chotentech.data.PowerManager.onReload
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import io.github.zzzyyylllty.chotentech.load.loadAccountTypes
import io.github.zzzyyylllty.chotentech.load.loadLevelFiles
import io.github.zzzyyylllty.chotentech.load.loadPowerFiles
import io.github.zzzyyylllty.chotentech.load.tempPowers
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
    RuntimeDependency(
        value = "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
        test = "!kotlinx.serialization.Serializer",
        relocate = [
            "!kotlin.", "!kotlin220.",
            "!kotlinx.serialization.", "!kotlinx.serialization181."
        ],
        transitive = false
    ),
    RuntimeDependency(
        value = "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
        test = "!kotlinx.serialization.json.Json",
        relocate = [
            "!kotlin.", "!kotlin220.",
            "!kotlinx.serialization.", "!kotlinx.serialization181."
        ],
        transitive = false
    )
)
object ChoTenTech : Plugin(), BukkitWorldGenerator {


    @Config("config.yml")
    lateinit var config: Configuration

    @Config("account-types.yml")
    lateinit var accountTypes: Configuration

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

    val dateTimeFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
    var devMode = true

    override fun onEnable() {

        info("Enable")
        Language.enableSimpleComponent = true
        reloadCustomConfig()
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
        onReload()
    }

    override fun onActive() {
        autoLoadChunk()
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
            onReload()
            devMode = config.getBoolean("debug",false)
            accountTypesList.clear()
            levelsList.clear()
            loadPowerFiles()
            loadSDClassFile()
            loadAccountTypes()
            loadLevelFiles()
        }
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