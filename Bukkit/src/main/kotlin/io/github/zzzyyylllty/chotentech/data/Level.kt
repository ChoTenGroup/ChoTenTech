package io.github.zzzyyylllty.chotentech.data

import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.Bukkit

data class SanityLevelConfig(
    val regen: Double = 0.0,
    val min: String = "40%",
    val safe: Boolean = false,
)

data class Level(
    val world: String,
    val region: ProtectedRegion? = null,
    var cluster: String = "I",
    var number: String? = null,
    var name: String,
    var survivalDifficulty: SurvivalDifficulty,
    val author: String,
    val tip: String,
    val noclips: List<NoclipPoint>,
    val prefix: String,
    val isPlaceable: Boolean,
    val guiMaterial: String?,
    val powers: Set<String>,
    val id: String,
    val music: String?,
    val dmz: Boolean = false,
    val sanity: SanityLevelConfig? = null,
) {
    fun getWorld() = Bukkit.getWorld(world)
    fun generateLore() : String{
        return """
            <white>层群 $cluster 层级 $number <white>-> [ <gray>$name<white> ]
            
            <white>等级 [ <gray>${survivalDifficulty.prefix}${survivalDifficulty.classname}<white> ] <gray><i>${survivalDifficulty.one},${survivalDifficulty.two},${survivalDifficulty.three}
            <white>作者 <gray><i>$author
            
            <reset><gray><i>$tip
        """.trimIndent()
    }
}