package io.github.zzzyyylllty.chotentech.load

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.world.World
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import io.github.zzzyyylllty.chotentech.ChoTenTech.accountTypes
import io.github.zzzyyylllty.chotentech.ChoTenTech.accountTypesList
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.util.Vector
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.severe
import taboolib.library.configuration.ConfigurationSection
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
        // Parse conditions and targetLoc from raw map BEFORE string conversion
        val rawConditions = (nConfigs as? Map<*, *>)?.get("conditions") as? Map<*, *>
        val conditions = rawConditions?.let { parseNoclipConditions(it) }

        val rawTargetLoc = (nConfigs as? Map<*, *>)?.get("targetLoc")

        val map = LinkedHashMap<String, String>()
        for (c in nConfigs as LinkedHashMap<String, Any>) {
            if (c.key != "conditions" && c.key != "targetLoc") {
                map[c.key] = c.value.toString()
            }
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

        // Parse targetLoc: single string or weighted list
        val targetLocation: Location
        val targetLocations: List<WeightedLocation>? = when (rawTargetLoc) {
            is String -> {
                targetLocation = parseLocation(rawTargetLoc.split(" "))
                null
            }
            is List<*> -> {
                val weighted = rawTargetLoc.mapNotNull { entry ->
                    val m = entry as? Map<*, *> ?: return@mapNotNull null
                    val locStr = m["loc"]?.toString() ?: return@mapNotNull null
                    val weight = m["weight"]?.toString() ?: "1"
                    WeightedLocation(parseLocation(locStr.split(" ")), weight)
                }
                targetLocation = weighted.firstOrNull()?.location
                    ?: parseLocation(map["targetLoc"]?.split(" ") ?: listOf("world", "0", "0", "0"))
                weighted.takeIf { it.isNotEmpty() }
            }
            else -> {
                targetLocation = parseLocation(map["targetLoc"]?.split(" ") ?: listOf("world", "0", "0", "0"))
                null
            }
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
            name = map["name"],
            targetLevel = map["target"],
            autoNoclip = map["autoNoclip"]?.equals("true", ignoreCase = true) == true,
            conditions = conditions,
            targetLocations = targetLocations,
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

    val world = Bukkit.getWorld(config.getString("$root.world") ?: "world") ?: Bukkit.getWorld("world")

    val randomNoclipMode = config.getString("$root.randomNoclip")?.let {
        when (it.uppercase()) { "EACH" -> RandomNoclipMode.EACH; "RESTART" -> RandomNoclipMode.RESTART; else -> null }
    }
    val randomGroups: List<List<String>>? = (config.getList("$root.randoms") ?: emptyList<Any>()).mapNotNull { group ->
        (group as? List<*>)?.mapNotNull { it?.toString() }?.takeIf { it.isNotEmpty() }
    }.takeIf { it.isNotEmpty() }

    levelsList[root] = Level(
        world = world!!.name,
        region = getRegion(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world), root),
        cluster = config.getString("$root.cluster") ?: "Unknown",
        number = config.getString("$root.number") ?: "Unknown",
        name = config.getString("$root.name") ?: "Unknown",
        survivalDifficulty = sdClass,
        author = config.getString("$root.author") ?: "Unknown",
        tip = config.getString("$root.tip") ?: "Unknown",
        noclips = noclipList,
        prefix = config.getString("$root.prefix") ?: "",
        isPlaceable = config.getBoolean("$root.isPlaceable"),
        guiMaterial = config.getString("$root.guiMaterial"),
        powers = (config.getList("$root.powers")?.toSet() as? Set<String>?) ?: emptySet(),
        root,
        config.getString("$root.music"),
        dmz = config.getBoolean("$root.dmz"),
        sanity = loadSanityConfig(config.getConfigurationSection("$root.sanity")),
        randomNoclip = randomNoclipMode,
        randomGroups = randomGroups,
    )
}

private fun loadSanityConfig(section: ConfigurationSection?): SanityLevelConfig? {
    if (section == null) return null
    return SanityLevelConfig(
        regen = section.getDouble("regen", 0.0),
        min = section.getString("min", "40%") ?: "40%",
        safe = section.getBoolean("safe", false),
    )
}

private fun parseNoclipConditions(raw: Map<*, *>): NoclipConditions {
    return NoclipConditions(
        item = (raw["item"] as? Map<*, *>)?.let { parseItemCondition(it) },
        car = (raw["car"] as? Map<*, *>)?.let { parseCarCondition(it) },
        coop = (raw["coop"] as? Map<*, *>)?.let { parseCoopCondition(it) },
        signal = (raw["signal"] as? Map<*, *>)?.let { parseSignalCondition(it) },
        dropbag = (raw["dropbag"] as? Map<*, *>)?.let { parseDropbagCondition(it) },
        condition = (raw["condition"] as? List<*>)?.let { parseConditionChain(it) },
    )
}

private fun parseItemCondition(raw: Map<*, *>): ItemCondition {
    val itemsRaw = raw["items"] as? List<*> ?: emptyList<Any>()
    val items = itemsRaw.mapNotNull { item ->
        val itemMap = item as? Map<*, *> ?: return@mapNotNull null
        ConditionItem(
            id = itemMap["id"]?.toString() ?: "",
            amount = (itemMap["amount"]?.toString() ?: "1").toIntOrNull() ?: 1,
            consume = itemMap["consume"]?.toString()?.let { it == "true" } ?: true,
        )
    }
    return ItemCondition(
        shared = raw["shared"]?.toString()?.let { it == "true" } ?: false,
        lifetime = raw["lifetime"]?.toString()?.let { it == "true" } ?: false,
        items = items,
        message = raw["message"]?.toString(),
    )
}

private fun parseCarCondition(raw: Map<*, *>): CarCondition {
    return CarCondition(
        shared = raw["shared"]?.toString()?.let { it == "true" } ?: false,
        lifetime = raw["lifetime"]?.toString()?.let { it == "true" } ?: false,
        cost = raw["cost"]?.toString() ?: "money",
        amount = (raw["amount"]?.toString() ?: "0").toLongOrNull() ?: 0L,
        maxPlayers = (raw["maxPlayers"]?.toString() ?: "4").toIntOrNull() ?: 4,
        message = raw["message"]?.toString(),
    )
}

private fun parseCoopCondition(raw: Map<*, *>): CoopCondition {
    return CoopCondition(
        require = (raw["require"]?.toString() ?: "2").toIntOrNull() ?: 2,
        max = (raw["max"]?.toString() ?: "5").toIntOrNull() ?: 5,
        message = raw["message"]?.toString(),
    )
}

private fun parseSignalCondition(raw: Map<*, *>): SignalCondition {
    val itemRaw = raw["item"]
    val items: Any = when (itemRaw) {
        is List<*> -> itemRaw.mapNotNull { it?.toString() }
        else -> itemRaw?.toString() ?: ""
    }
    val locStr = raw["loc"]?.toString()
    val (world, loc1, loc2) = if (locStr != null) {
        val parts = locStr.split(" ")
        if (parts.size >= 7) {
            Triple(
                parts[0],
                Vector(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble()),
                Vector(parts[4].toDouble(), parts[5].toDouble(), parts[6].toDouble()),
            )
        } else Triple(null, null, null)
    } else Triple(null, null, null)
    return SignalCondition(
        item = items,
        world = world,
        location1 = loc1,
        location2 = loc2,
        message = raw["message"]?.toString(),
    )
}

private fun parseDropbagCondition(raw: Map<*, *>): DropbagCondition {
    return DropbagCondition(
        armor = raw["armor"]?.toString()?.let { it == "true" },
        inventory = raw["inventory"]?.toString()?.let { it == "true" },
        hotbar = raw["hotbar"]?.toString()?.let { it == "true" } ?: true,
        helmet = raw["helmet"]?.toString()?.let { it == "true" },
        chestplate = raw["chestplate"]?.toString()?.let { it == "true" },
        leggings = raw["leggings"]?.toString()?.let { it == "true" },
        boots = raw["boots"]?.toString()?.let { it == "true" },
        message = raw["message"]?.toString(),
    )
}

private fun parseLocation(parts: List<String>): Location {
    val world = Bukkit.getWorld(parts[0]) ?: Bukkit.getWorlds().first()
    val x = parts.getOrElse(1) { "0" }.toDouble()
    val y = parts.getOrElse(2) { "0" }.toDouble()
    val z = parts.getOrElse(3) { "0" }.toDouble()
    val yaw = parts.getOrElse(4) { "0" }.toFloat()
    val pitch = parts.getOrElse(5) { "0" }.toFloat()
    return Location(world, x, y, z, yaw, pitch)
}

private fun parseConditionChain(raw: List<*>): List<ConditionChainEntry> {
    return raw.mapNotNull { entry ->
        val map = entry as? Map<*, *> ?: return@mapNotNull null
        ConditionChainEntry(
            kether = map["kether"]?.toString(),
            message = map["message"]?.toString(),
            yes = map["yes"]?.toString()?.let { it == "true" } ?: false,
        )
    }
}
