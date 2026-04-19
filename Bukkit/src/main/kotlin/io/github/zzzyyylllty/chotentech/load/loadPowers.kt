package io.github.zzzyyylllty.chotentech.load

import io.github.zzzyyylllty.chotentech.data.*
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import io.github.zzzyyylllty.chotentech.util.asListEnhanced
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
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

val tempPowers = mutableListOf<Power>()

fun loadPowerFiles() {
    tempPowers.clear()
    if (!File(getDataFolder(), "powers").exists()) {
        releaseResourceFolder("powers")
    }
    val files = File(getDataFolder(), "powers").listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) file.listFiles()?.forEach {
            loadPowerFile(it)
        }
        else loadPowerFile(file)
    }
    reloadAllPowers(tempPowers)
}
fun loadPowerFile(file: File) {
    if (file.isDirectory) file.listFiles()?.forEach {
        loadPowerFile(it)
    } else {
        val conf = Configuration.loadFromFile(file)
        conf.getKeys(false).forEach { key ->
            loadPower(key, conf)
        }
    }
}

fun loadPower(root: String, conf: Configuration): Power {

    val section = conf.getConfigurationSection(root) ?: error("power section not found")
    val agentRaw = section.getConfigurationSection("agent")
    val agents = HashMap<String, List<String>>()

    agentRaw?.getKeys(false)?.forEach { key ->
        agentRaw.get(key).asListEnhanced()?.let { agents[key] = it }
    }

    val power = Power(
        name = section.requireString("name"),
        leverPos = section.requirePos("leverPos"),
        openDelaySeconds = section.requireInt("openDelaySeconds"),
        activeDurationSeconds = section.requireInt("activeDurationSeconds"),
        obstacles = loadObstacles(section),
        openingAnimation = loadPowerAnimation(section.getConfigurationSection("openingAnimation")),
        closingAnimation = loadPowerAnimation(section.getConfigurationSection("closingAnimation")),
        agents = agents
    )
    tempPowers.add(power)
    return power
}

private fun loadObstacles(section: ConfigurationSection): List<ObstacleConfig> {
    val obstacleRoot = section.getConfigurationSection("obstacles") ?: return emptyList()

    return obstacleRoot.getKeys(false).map { obstacleKey ->
        val obstacleSection = obstacleRoot.getConfigurationSection(obstacleKey)
            ?: error("Missing obstacle section: obstacles.$obstacleKey")

        ObstacleConfig(
            id = obstacleSection.getString("id") ?: obstacleKey,
            blocks = loadPosList(obstacleSection, "blocks"),
            cuboid = loadCuboid(obstacleSection, "cuboid"),
            mode = parseEnum(
                obstacleSection.getString("mode"),
                ObstacleMode.RESTORE_BLOCKS
            ),
            sequentialConfig = loadSequentialConfig(
                obstacleSection.getConfigurationSection("sequentialConfig")
            )
        )
    }
}

private fun loadPowerAnimation(section: ConfigurationSection?): PowerAnimation {
    val def = PowerAnimation()
    if (section == null) return def

    return PowerAnimation(
        enabled = section.getBoolean("enabled", def.enabled),
        particle = parseEnum(section.getString("particle"), def.particle),
        particleCount = section.getInt("particleCount", def.particleCount),
        sound = section.getString("sound")?.let { NamespacedKey.fromString(it) }?.let { Registry.SOUNDS.get(it) },
        volume = section.getDouble("volume", def.volume.toDouble()).toFloat(),
        pitch = section.getDouble("pitch", def.pitch.toDouble()).toFloat(),
        durationTicks = section.getLong("durationTicks", def.durationTicks),
        intervalTicks = section.getLong("intervalTicks", def.intervalTicks)
    )
}

private fun loadSequentialConfig(section: ConfigurationSection?): ObstacleSequentialConfig {
    val def = ObstacleSequentialConfig()
    if (section == null) return def

    return ObstacleSequentialConfig(
        enabled = section.getBoolean("enabled", def.enabled),
        openDirection = parseEnum(section.getString("openDirection"), def.openDirection),
        reverseOnClose = section.getBoolean("reverseOnClose", def.reverseOnClose),
        blocksPerStep = section.getInt("blocksPerStep", def.blocksPerStep),
        stepDelayTicks = section.getLong("stepDelayTicks", def.stepDelayTicks),
        openEffect = loadProgressEffect(
            section.getConfigurationSection("openEffect"),
            ProgressEffect()
        ),
        closeEffect = loadProgressEffect(
            section.getConfigurationSection("closeEffect"),
            ProgressEffect(
                particle = Particle.WHITE_SMOKE,
                count = 6
            )
        )
    )
}

private fun loadProgressEffect(section: ConfigurationSection?, default: ProgressEffect): ProgressEffect {
    if (section == null) return default

    return ProgressEffect(
        enabled = section.getBoolean("enabled", default.enabled),
        particle = parseEnum(section.getString("particle"), default.particle),
        count = section.getInt("count", default.count),
        offsetX = section.getDouble("offsetX", default.offsetX),
        offsetY = section.getDouble("offsetY", default.offsetY),
        offsetZ = section.getDouble("offsetZ", default.offsetZ),
        speed = section.getDouble("speed", default.speed),
        sound = section.getString("sound")?.let { NamespacedKey.fromString(it) }?.let { Registry.SOUNDS.get(it) },
        volume = section.getDouble("volume", default.volume.toDouble()).toFloat(),
        pitch = section.getDouble("pitch", default.pitch.toDouble()).toFloat()
    )
}

private fun loadPosList(section: ConfigurationSection, path: String): List<Pos> {
    val posSection = section.getConfigurationSection(path) ?: return emptyList()

    return posSection.getKeys(false)
        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
        .map { key ->
            posSection.requirePos(key)
        }
}

private fun loadCuboid(section: ConfigurationSection, path: String): Cuboid? {
    val cuboidSection = section.getConfigurationSection(path) ?: return null
    return Cuboid(
        pos1 = cuboidSection.requirePos("pos1"),
        pos2 = cuboidSection.requirePos("pos2")
    )
}

private fun ConfigurationSection.requireString(path: String): String =
    getString(path) ?: error("Missing string: $path")

private fun ConfigurationSection.requireInt(path: String): Int =
    if (contains(path)) getInt(path) else error("Missing int: $path")

private fun ConfigurationSection.requirePos(path: String): Pos {
    val sec = getConfigurationSection(path) ?: error("Missing Pos section: $path")
    return Pos(
        world = sec.requireString("world"),
        x = sec.requireInt("x"),
        y = sec.requireInt("y"),
        z = sec.requireInt("z")
    )
}

private inline fun <reified T : Enum<T>> parseEnum(value: String?, default: T): T {
    if (value.isNullOrBlank()) return default
    return runCatching { enumValueOf<T>(value.uppercase()) }.getOrElse { default }
}
