package io.github.zzzyyylllty.chotentech.load

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.world.World
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import io.github.zzzyyylllty.chotentech.ChoTenTech.accountTypes
import io.github.zzzyyylllty.chotentech.ChoTenTech.accountTypesList
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.IDType
import io.github.zzzyyylllty.chotentech.data.Level
import io.github.zzzyyylllty.chotentech.data.NoclipPoint
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.util.Vector
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.severe
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.LinkedHashMap

/*
*
    val region: ProtectedRegion? = null,
    var cluster: String = "I",
    var number: String = "0",
    var name: String,
    var survivalDifficulty: SurvivalDifficulty,
    val author: String,
    val tip: String,
    val noclips: List<NoclipPoint>,
* */

fun loadLevelFiles() {
    if (!File(getDataFolder(), "levels").exists()) {
        releaseResourceFolder("levels")
    }
    val files = File(getDataFolder(), "levels").listFiles() ?: return
    for (file in files) {
        // If directory load file in it...
        if (file.isDirectory) file.listFiles()?.forEach {
            loadLevelFile(it)
        }
        else loadLevelFile(file)
    }
}
fun loadLevelFile(file: File) {
    if (file.isDirectory) file.listFiles()?.forEach {
        loadLevelFile(it)
    } else {
        val conf = Configuration.loadFromFile(file)
        conf.getKeys(false).forEach { key ->
            loadLevel(key, conf)
        }
    }
}

fun getRegion(
    world: World,
    regionid: String,
): ProtectedRegion? {
    val container = WorldGuard.getInstance().platform.regionContainer
    val regions = container[world]
    if (regions != null) {
        if (regions.getRegion(regionid) != null) {
            info("获取指定的区域 $regionid 于 ${world.name} 成功。正在返回 ${regions.getRegion(regionid)}")
            return regions.getRegion(regionid)
        } else {
            severe("无法获取指定的区域 $regionid 于 $world，将返回 null！")
            return null
        }
    } else {
        severe("$world 不存在任何区域！将返回 null！")
        return null
    }
}


fun loadLevel(root: String, config: Configuration) {

    val noclipList = mutableListOf<NoclipPoint>()

    for (nConfigs in config.getList("$root.noclips") ?: emptyList<NoclipPoint>()) {
        val map = LinkedHashMap<String, String>()
        for (c in nConfigs as LinkedHashMap<String, Any>) {
            map[c.key] = c.value.toString()
        }
        // 读取并规范化位置 world;x,y,z;x1,y1,z1

        val splitLoc: List<String> = map["noclipPointLoc"]!!.split(" ")
        val noclipPointBukkitWorld = splitLoc[0]
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

    val sdClass =
        if (config.isConfigurationSection("$root.sdclass")) {
            val section = config.getConfigurationSection("$root.sdclass")
            SurvivalDifficulty(
                classname = section?.getString("classname") ?: "Class 0",
                one = section?.getString("one") ?: "安全",
                two = section?.getString("two") ?: "稳定",
                three = section?.getString("three") ?: "实体绝迹",
                prefix = section?.getString("prefix") ?: "<#66CCFF>"
            )
        } else {
            sdClassList[config.getString("$root.sdclass") ?: "1"] ?: SurvivalDifficulty(
                classname = "Class 0",
                one = "安全",
                two = "稳定",
                three = "实体绝迹",
                prefix = "<#66CCFF>"
            )
        }

    levelsList[root] = Level(
        region = getRegion(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(Bukkit.getWorld(config.getString("$root.world") ?: "world")), root),
        cluster = config.getString("$root.cluster") ?: "Unknown",
        number = config.getString("$root.number") ?: "Unknown",
        name = config.getString("$root.name") ?: "Unknown",
        survivalDifficulty = sdClass,
        author = config.getString("$root.author") ?: "Unknown",
        tip = config.getString("$root.tip") ?: "Unknown",
        noclips = noclipList,
        prefix = config.getString("$root.prefix") ?: "",
    )
}
