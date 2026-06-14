package io.github.zzzyyylllty.quetiapine.listener

import com.sk89q.worldguard.util.Locations
import io.github.zzzyyylllty.chotentech.ChoTenTech.blockRegenMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.levels
import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplates
import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplatesByBlock
import io.github.zzzyyylllty.quetiapine.data.LevelDataManager
import io.github.zzzyyylllty.quetiapine.data.PlayerDataManager
import io.github.zzzyyylllty.quetiapine.data.Regen
import io.github.zzzyyylllty.quetiapine.data.RegenBlock
import io.github.zzzyyylllty.quetiapine.data.RegenTemplate
import io.github.zzzyyylllty.quetiapine.event.QuetiapineAddExpEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineAddLevelEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineLevelUpgradeEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineRemoveLevelEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineSetExpEvent
import io.github.zzzyyylllty.quetiapine.event.QuetiapineSetLevelEvent
import io.github.zzzyyylllty.quetiapine.util.DependencyHelper
import io.github.zzzyyylllty.quetiapine.util.devLog
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
import net.momirealms.craftengine.core.util.Key
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.event.block.BlockBreakEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.expansion.getDataContainer
import java.util.LinkedList
import kotlin.math.roundToLong
import kotlin.time.Clock

@SubscribeEvent(EventPriority.MONITOR)
fun onLevelUpgrade(e: QuetiapineLevelUpgradeEvent) {
    val player = e.player
    val agents = levels[e.levelName]?.agents ?: return
    agents.runAgent("onUpgrade", linkedMapOf("event" to e, "player" to player, "level" to e.targetLevel), player)

    for (level in e.sourceLevel..e.targetLevel) {
        agents.runAgent("onUpgradeSingle", linkedMapOf("event" to e, "player" to player, "level" to level), player)
    }

}
