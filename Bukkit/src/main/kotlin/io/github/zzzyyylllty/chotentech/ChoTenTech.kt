package io.github.zzzyyylllty.chotentech

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.Flag
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.PlayerData
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import io.github.zzzyyylllty.chotentech.load.loadSDClassFile
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
import taboolib.module.ui.type.Linked

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

    var MY_CUSTOM_FLAG: StateFlag? = null


    val plugin by lazy { this }
    var dataFolder = nativeDataFolder()
    val console by lazy { console() }
    val consoleSender by lazy { console.castSafely<CommandSender>()!! }
    val dataMap: LinkedHashMap<String, PlayerData> = linkedMapOf()
    val sdClassList: LinkedHashMap<String, SurvivalDifficulty> = linkedMapOf()
    val levelsList: LinkedHashMap<String, Level> = linkedMapOf()

    val config by lazy { Configuration.loadFromFile(newFile(getDataFolder(), "config.yml", create = true), Type.YAML) }

    val placeHolderConfig by lazy { Configuration.loadFromFile(newFile(getDataFolder(), "placeholders.yml", create = true), Type.YAML) }

    override fun onLoad() {

        val registry = WorldGuard.getInstance().getFlagRegistry()
        try {
            // create a flag with the name "my-custom-flag", defaulting to true
            val flag = StateFlag("my-custom-flag", true)
            registry.register(flag)
            MY_CUSTOM_FLAG = flag // only set our field if there was no error
        } catch (e: FlagConflictException) {
            // some other plugin registered a flag by the same name already.
            // you can use the existing flag, but this may cause conflicts - be sure to check type
            val existing: Flag<*>? = registry.get("my-custom-flag")
            if (existing is StateFlag) {
                MY_CUSTOM_FLAG = existing
            } else {
                // types don't match - this is bad news! some other plugin conflicts with you
                // hopefully this never actually happens
            }
        }
    }


    // 项目使用TabooLib Start Jar 创建!
    override fun onEnable() {
        reloading()
    }

    fun reloading() {
        config.reload()
        // loadLootFiles()
        loadSDClassFile()
    }
}