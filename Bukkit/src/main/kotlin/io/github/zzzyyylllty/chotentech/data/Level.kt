package io.github.zzzyyylllty.chotentech.data

import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.Bukkit

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
) {
    fun getWorld() = Bukkit.getWorld(world)
    fun generateLore() : String{
        return """
            <white><b>层群 $cluster 层级 $number <white><b>-> [ <gray><b>$name<white><b> ]
            
            <white><b>生存难度等级 [ <gray>${survivalDifficulty.prefix}<b>${survivalDifficulty.classname}<white><b> ] <gray><i>${survivalDifficulty.one},${survivalDifficulty.two},${survivalDifficulty.three}
            <white><b>作者 <gray><i>$author
            <reset><gray><i>$tip
        """.trimIndent()
    }
}