package io.github.zzzyyylllty.chotentech

import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import io.github.zzzyyylllty.chotentech.db.SQLDataBase
import io.github.zzzyyylllty.chotentech.function.test.testFunction
import io.github.zzzyyylllty.chotentech.function.timer.timeEffects
import io.github.zzzyyylllty.chotentech.hook.PapiHookOriginal
import io.github.zzzyyylllty.chotentech.load.loadLevel
import io.github.zzzyyylllty.chotentech.load.loadSDClassFile
import io.github.zzzyyylllty.chotentech.worldgeneration.Level0Generator
import io.github.zzzyyylllty.chotentech.worldgeneration.Level1Generator
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.generator.ChunkGenerator
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.io.newFile
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.util.random
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import taboolib.platform.BukkitPlugin
import taboolib.platform.BukkitWorldGenerator
import java.util.UUID

@RuntimeDependencies(
    RuntimeDependency(
        value = "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3",
        test = "!kotlinx.serialization.Serializer",
        relocate = ["!kotlin.", "!kotlin1922.","!kotlinx.serialization.", "!kotlinx.serialization163."],
        transitive = false
    ),
    RuntimeDependency(
        value = "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3",
        test = "!kotlinx.serialization.json.Json",
        relocate = ["!kotlin.", "!kotlin1922.","!kotlinx.serialization.", "!kotlinx.serialization163."],
        transitive = false
    ),
)
object ChoTenTech : Plugin(), BukkitWorldGenerator {

    val plugin by lazy { this }
    var dataFolder = nativeDataFolder()
    val console by lazy { console() }
    val consoleSender by lazy { console.castSafely<CommandSender>()!! }
    val dataMap: LinkedHashMap<String, PlayerData> = linkedMapOf()
    val sdClassList: LinkedHashMap<String, SurvivalDifficulty> = linkedMapOf()
    val levelsList: LinkedHashMap<String, Level> = linkedMapOf()
    val playerRegions = mutableMapOf<UUID, Set<String>>()

    val config by lazy { Configuration.loadFromFile(newFile(getDataFolder(), "config.yml", create = true), Type.YAML) }

    val placeHolderConfig by lazy { Configuration.loadFromFile(newFile(getDataFolder(), "placeholders.yml", create = true), Type.YAML) }

    // 项目使用TabooLib Start Jar 创建!
    override fun onActive() {
        PapiHookOriginal(BukkitPlugin.getInstance()).register()
        reloading()
        timeEffects()
    }

    override fun onEnable() {
        testFunction()
    }

    override fun onDisable() {
        for (player in Bukkit.getOnlinePlayers()) SQLDataBase().saveInDatabase(player)
    }
    fun reloading() {
        config.reload()
        // loadLootFiles()
        loadLevel()
        loadSDClassFile()
    }

    override fun getDefaultWorldGenerator(worldName: String?, name: String?): ChunkGenerator? {
        return when (worldName) {
            "level0inf" -> Level0Generator()
            "level1inf" -> Level1Generator()
            else -> null
        }
    }
}

