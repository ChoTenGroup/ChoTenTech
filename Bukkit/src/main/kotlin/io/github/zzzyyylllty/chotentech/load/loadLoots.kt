package io.github.zzzyyylllty.chotentech.load

import com.sun.jdi.Location
import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.data.Loot
import io.github.zzzyyylllty.chotentech.data.LootItem
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import java.io.File


fun loadItemFiles() {
    if (!File(getDataFolder(), "loots").exists()) {
        releaseResourceFile("loots/test.yml")
    }

    val files = File(getDataFolder(), "loots").listFiles()

    if (files == null) {
        return
    }

    for (file in files) {
        // If directory load file in it...
        if (file.isDirectory) file.listFiles()?.forEach {
            loadItemFile(it)
        }
        else loadItemFile(file)
    }
}

fun loadItemFile(file: File) {

    val config = YamlConfiguration.loadConfiguration(file)

    for (iroot in config.getKeys(false)) {
        config.getConfigurationSection(iroot)?.let { loadLoot(it) }
    }
}

fun loadLoot(config: ConfigurationSection) {

    val items = mutableListOf<LootItem>()

    for (map in (config.getList("items") as List<LinkedHashMap<String, String>>)) {
        items.add(LootItem(map["item"] ?: "MI:STEEL_SWORD", map["weight"]?.toDouble() ?: 1.0))
    }

    val loot = Loot(
        searchAmount = config.getString("amount", "1") ?: "1",
        items = TODO(),
        lootItems = TODO(),
        refreshTime = TODO(),
        refreshTimeEnd = TODO()
    )

    for (string in (config.getList("locations") as List<String>)) {
        val str = string.split(",")
        val world = Bukkit.getWorld(str[0]) ?: throw NullPointerException("World ${str[0]} is null")
        ChoTenTech.lootList.put(org.bukkit.Location(world,str[1].toDouble(),str[2].toDouble(),str[3].toDouble()), loot)
    }
}
