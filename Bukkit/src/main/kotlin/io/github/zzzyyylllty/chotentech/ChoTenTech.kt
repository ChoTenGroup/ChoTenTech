

package io.github.zzzyyylllty.chotentech

import io.github.zzzyyylllty.chotentech.data.IDType
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.PlayerHUDData
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import io.github.zzzyyylllty.chotentech.load.loadAccountTypes
import io.github.zzzyyylllty.chotentech.load.loadLevelFiles
import org.bukkit.command.CommandSender
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.console
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.database.getHost
import taboolib.module.lang.Language
import taboolib.platform.BukkitPlugin
import java.time.format.DateTimeFormatter

@RuntimeDependencies(
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
        test = "!kotlinx.serialization.Serializer",
        relocate = [
            "!kotlin.", "!kotlin220.",
            "!kotlinx.serialization.", "!kotlinx.serialization181."
        ],
        transitive = false
    ),
    RuntimeDependency(
        value = "!com.google.code.gson:gson:2.10.1",
        relocate = ["!com.google.gson", "!com.example.library.gson"]
    )
)
object ChoTenTech : Plugin() {


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
    val playerRegions = mutableMapOf<String, Set<String>>()
    val playerDataMap = mutableMapOf<String, PlayerData>()
    val playerHudMap = mutableMapOf<String, PlayerHUDData>()

    val dateTimeFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
    var devMode = true

    override fun onEnable() {

        info("Enable")
        Language.enableSimpleComponent = true
        reloadCustomConfig()

    }

    override fun onDisable() {
        info("Disable")
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
            devMode = config.getBoolean("debug",false)
            accountTypesList.clear()
            levelsList.clear()
            loadAccountTypes()
            loadLevelFiles()
        }
    }


}
