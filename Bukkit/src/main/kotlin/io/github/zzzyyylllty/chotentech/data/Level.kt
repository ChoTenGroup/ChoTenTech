package io.github.zzzyyylllty.chotentech.data

import com.sk89q.worldguard.protection.regions.ProtectedRegion
import net.kyori.adventure.text.Component
import java.time.LocalDateTime

data class Level(
    val region: ProtectedRegion? = null,
    var cluster: String = "I", // 层群，默认为 I ，为 I 则不显示。请注意，I为主层群。
    var number: String = "0", // 不赋值则为0。该数值在ENIGMATIC/JOKE/SUBLAYER类别时无效。
    var name: String,
    var survivalDifficulty: SurvivalDifficulty,
    val author: String,
    val tip: String,
    val noclips: List<NoclipPoint>,
) {
    fun generateLore() : String{
        return """
            <white><b>Cluster $cluster
            <white><b> Level $number <white><b>-> [ <gray><b>$name<white><b> ]
            
            <white><b> 生存难度等级 [ <gray>${survivalDifficulty.prefix}<b>${survivalDifficulty.classname}<white><b> ]
            <white><b> 作者 $author
            <reset><gray><i> $tip
        """.trimIndent()
    }
}