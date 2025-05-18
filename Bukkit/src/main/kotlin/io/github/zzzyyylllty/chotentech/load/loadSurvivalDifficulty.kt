package io.github.zzzyyylllty.chotentech.load

import io.github.zzzyyylllty.chotentech.ChoTenTech
import io.github.zzzyyylllty.chotentech.ChoTenTech.sdClassList
import io.github.zzzyyylllty.chotentech.data.Loot
import io.github.zzzyyylllty.chotentech.data.LootItem
import io.github.zzzyyylllty.chotentech.data.SurvivalDifficulty
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import java.io.File


fun loadSDClassFile() {

    val config = YamlConfiguration.loadConfiguration(File(getDataFolder(), "sdclass.yml"))

    for (iroot in config.getKeys(false)) {
        config.getConfigurationSection(iroot)?.let { loadSDClass(iroot,it) }
    }
}

fun loadSDClass(root: String, config: ConfigurationSection) {

    sdClassList.put(root,SurvivalDifficulty(
        classname = config.getString("classname") ?: "Unknown",
        one = config.getString("one") ?: "Unknown",
        two = config.getString("two") ?: "Unknown",
        three = config.getString("three") ?: "Unknown",
        prefix = config.getString("prefix") ?: "Unknown",
    ))
}
