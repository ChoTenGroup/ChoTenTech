package io.github.zzzyyylllty.chotentech.load

import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.NoclipPoint
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.util.Vector
import taboolib.common.LifeCycle
import taboolib.common.io.newFolder
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.module.configuration.Configuration
import java.util.LinkedHashMap

val read = mutableListOf<Configuration>()

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

    val noclipList = mutableListOf<NoclipPoint>()

    for (nConfigs in config.getList("$root.noclips") ?: emptyList<NoclipPoint>()) {
        val map = LinkedHashMap<String, String>()
        for (c in nConfigs as LinkedHashMap<String, Any>) {
            map.put(c.key, c.value.toString())
        }
        // 读取并规范化位置 world;x,y,z;x1,y1,z1

        val splitLoc: List<String> = map["noclipPointLoc"]!!.split(" ")
        val noclipPointBukkitWorld = Bukkit.getWorld(splitLoc[0])
        val loc1x: Double = splitLoc[1].toDouble()
        val loc1y: Double = splitLoc[2].toDouble()
        val loc1z: Double = splitLoc[3].toDouble()
        val loc2x: Double = splitLoc[4].toDouble()
        val loc2y: Double = splitLoc[5].toDouble()
        val loc2z: Double = splitLoc[6].toDouble()
        val loc1 = Vector(loc1x, loc1y, loc1z)
        val loc2 = Vector(loc2x, loc2y, loc2z)

        // 读取并规范化位置 world,x,y,z,yaw,pitch
        val targetLocResolver: List<String> = map["targetLoc"]!!.split(" ")
        val targetLocation: Location = if (targetLocResolver.size >= 6) {
            Location(
                Bukkit.getWorld(targetLocResolver[0]),
                targetLocResolver[1].toDouble(),
                targetLocResolver[2].toDouble(),
                targetLocResolver[3].toDouble(),
                targetLocResolver[4].toFloat(),
                targetLocResolver[5].toFloat()
            )
        } else {
            Location(
                Bukkit.getWorld(targetLocResolver[0]),
                targetLocResolver[1].toDouble(),
                targetLocResolver[2].toDouble(),
                targetLocResolver[3].toDouble()
            )
        }

        noclipList.add(NoclipPoint(
            noclipPointWorld = noclipPointBukkitWorld,
            noclipPointLocation1 = loc1,
            noclipPointLocation2 = loc2,
            targetLocation = targetLocation,
            basicSuccessRate = (map["successRate"] ?: "10.0").toDouble(),
            rangeDecay = 1.0,
            hunger = (map["hunger"] ?: "0.0").toDouble(),
            damage = (map["damage"] ?: "0.0").toDouble(),
            cooldown = (map["cooldown"] ?: "5.0").toDouble(),
            noclippingTime = (map["time"] ?: "5.0").toDouble(),
            name = map["name"]
        ))
    }

    levelsList.put(root, Level(
        region = getRegion(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(Bukkit.getWorld(config.getString("$root.world") ?: "world")), root),
        cluster = config.getString("$root.cluster") ?: "Unknown",
        number = config.getString("$root.number") ?: "Unknown",
        name = config.getString("$root.name") ?: "Unknown",
        survivalDifficulty = sdClassList[config.getString("$root.sdclass") ?: "1"] ?: SurvivalDifficulty(
            classname = "Class 0",
            one = "安全",
            two = "稳定",
            three = "实体绝迹",
            prefix = "<#66CCFF>"
        ),
        author = config.getString("$root.author") ?: "Unknown",
        tip = config.getString("$root.tip") ?: "Unknown",
        noclips = noclipList
    ))
}
