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
    val randomNoclip: RandomNoclipMode? = null,
    val randomGroups: List<List<String>>? = null,
) {
    fun getWorld() = Bukkit.getWorld(world)
    fun generateLore() : String{
        return """
            <#F2F5FA>层群 $cluster 层级 $number <#F2F5FA>-> [ <#A9B4C3>$name<#F2F5FA> ]
            
            <#F2F5FA>等级 [ <#A9B4C3>${survivalDifficulty.prefix}${survivalDifficulty.classname}<#F2F5FA> ] <#A9B4C3><i>${survivalDifficulty.one},${survivalDifficulty.two},${survivalDifficulty.three}
            <#F2F5FA>作者 <#A9B4C3><i>$author
            
            <reset><#A9B4C3><i>$tip
        """.trimIndent()
    }
}