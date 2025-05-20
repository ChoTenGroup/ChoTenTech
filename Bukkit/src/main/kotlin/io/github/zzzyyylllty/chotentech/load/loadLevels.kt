package io.github.zzzyyylllty.chotentech.load

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.LifeCycle
import taboolib.common.io.newFolder
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import java.io.File
import taboolib.module.configuration.Configuration
import taboolib.platform.BukkitAdapter

val read = mutableListOf<Configuration>()

@Awake(LifeCycle.ENABLE)
fun loadLevel() {
    read.clear()
    val file = newFolder(getDataFolder(), "levels", create = false)
    // 文件不存在则释放jar内的文件
    if (!file.exists()) {
        file.mkdirs()
        releaseResourceFile("levels/cluster1.yml")
    }
    file.walk()
        .filter { it.isFile }
        .filter { it.extension == "yaml" || it.extension == "yml" }
        .forEach {
            loadLevelFile(Configuration.loadFromFile(it))
        }
}


fun loadLevelFile(config: Configuration) {

    for (iroot in config.getKeys(false)) {
        loadLevel(iroot,config)
    }
}

fun loadLevel(root: String, config: Configuration) {

    levelsList.put(root, Level(
        region = getRegion(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(Bukkit.getWorld(config.getString("world") ?: "world")), root),
        cluster = config.getString("cluster") ?: "Unknown",
        number = config.getString("number") ?: "Unknown",
        name = config.getString("name") ?: "Unknown",
        survivalDifficulty = sdClassList[config.getString("sdclass") ?: "1"] ?: SurvivalDifficulty(
            classname = "Class 0",
            one = "安全",
            two = "稳定",
            three = "实体绝迹",
            prefix = "<#66CCFF>"
        ),
        author = config.getString("author") ?: "Unknown",
        tip = config.getString("tip") ?: "Unknown",
        noclips = emptyList() // TODO
    ))
}
