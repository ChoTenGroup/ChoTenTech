package io.github.zzzyyylllty.chotentech.data

import com.sk89q.worldguard.protection.regions.ProtectedRegion
import net.kyori.adventure.text.Component
import java.time.LocalDateTime

data class Level(
    val region: ProtectedRegion,
    var cluster: String = "I", // 层群，默认为 I ，为 I 则不显示。请注意，I为主层群。
    var number: Number = 0, // 不赋值则为0。该数值在ENIGMATIC/JOKE/SUBLAYER类别时无效。
    var name: Component,
    var survivalDifficulty: SurvivalDifficulty,
    val author: Component,
    val loot: Component,
    val tip: Component,
    val noclips: List<NoclipPoint>,
)
