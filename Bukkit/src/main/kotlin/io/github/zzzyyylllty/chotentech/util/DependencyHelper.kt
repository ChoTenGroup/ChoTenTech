package io.github.zzzyyylllty.chotentech.util

import org.bukkit.Bukkit


val dependencies = listOf(
    "adventure",
    "caffeine",
    "gson",
)

object DependencyHelper {

    val wg by lazy {
        isPluginInstalled("WorldGuard")
    }

    val papi by lazy {
        isPluginInstalled("PlaceholderAPI")
    }

    val attribute by lazy {
        isPluginInstalled("attribute")
    }

    val ce by lazy {
        isPluginInstalled("CraftEngine")
    }

    val luckPerms by lazy {
        isPluginInstalled("LuckPerms")
    }




    fun isPluginInstalled(name: String): Boolean {
        return (Bukkit.getPluginManager().getPlugin(name) != null)
    }

    fun isPremium() {

    }
}


