package io.github.zzzyyylllty.chotentech

import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.Loot
import io.github.zzzyyylllty.chotentech.load.loadItemFiles
import org.bukkit.Location
import org.bukkit.command.CommandSender
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.io.newFile
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
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
object ChoTenTech : Plugin() {

    val plugin by lazy { this }
    var dataFolder = nativeDataFolder()
    val console by lazy { console() }
    val consoleSender by lazy { console.castSafely<CommandSender>()!! }
    val dataMap: LinkedHashMap<UUID, PlayerData> = linkedMapOf()
    val lootList: LinkedHashMap<Location, Loot> = linkedMapOf()

    val config by lazy { Configuration.loadFromFile(newFile(getDataFolder(), "config.yml", create = true), Type.YAML) }

    val placeHolderConfig by lazy { Configuration.loadFromFile(newFile(getDataFolder(), "placeholders.yml", create = true), Type.YAML) }

    // 项目使用TabooLib Start Jar 创建!
    override fun onEnable() {
        reloading()
    }

    fun reloading() {
        // loadItemFiles()
    }
}