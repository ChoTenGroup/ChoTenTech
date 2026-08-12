package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.ChoTenTech.blockRegenMap
import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplates
import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplatesByBlock
import io.github.zzzyyylllty.chotentech.manager.AttributeManager
import io.github.zzzyyylllty.chotentech.quetiapine.data.MiningPowerManager
import io.github.zzzyyylllty.chotentech.quetiapine.data.Regen
import io.github.zzzyyylllty.chotentech.quetiapine.data.RegenBlock
import io.github.zzzyyylllty.chotentech.quetiapine.data.RegenTemplate
import io.github.zzzyyylllty.chotentech.quetiapine.data.load.PickaxeLevelManager
import io.github.zzzyyylllty.chotentech.quetiapine.util.DependencyHelper
import io.github.zzzyyylllty.chotentech.quetiapine.util.devLog
import io.github.zzzyyylllty.chotentech.quetiapine.util.toComponent
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
import net.momirealms.craftengine.core.util.Key
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.service.PlatformExecutor
import java.util.LinkedList
import kotlin.math.roundToLong

private var regenBlockTask: PlatformExecutor.PlatformTask? = null

val blockAdapters = LinkedHashMap<String, BlockAdapter>()


@SubscribeEvent
fun onRegenBlockBreak(e: BlockBreakEvent) {
    val player = e.player
    if (player.gameMode == GameMode.CREATIVE) return

    val regen = blockRegenMap[e.block.location]

    // 如果正在刷新中直接返回
    if ((regen?.end ?: 0) > System.currentTimeMillis()) return

    // 获取方块ID
    val id = getBlockID(e.block)
    devLog("ID $id")
    val templates = regenTemplatesByBlock[id] ?: return
    devLog("templates $templates")

    var template: RegenTemplate? = null

    for (templateID in templates) {
        val cTemplate = regenTemplates[templateID] ?: continue
        devLog("template $cTemplate")

        // Post条件（不满足则跳过此模板）
        if (!(cTemplate.postCondition?.validate(mapOf("event" to e, "block" to e.block), player) ?: true)) {
            devLog("Post-Condition not met, continue")
            continue
        }
        // 条件（满足则使用此模板）
        if (cTemplate.condition?.validate(mapOf("event" to e, "block" to e.block), player) ?: true) {
            devLog("Condition met,break")
            template = cTemplate
            break
        }
    }

    if (template == null) {
        devLog("template not found")
        return
    }

    // 作物阶段检查
    if (template.cropType != null) {
        val ageable = e.block.blockData as? org.bukkit.block.data.Ageable
        if (ageable != null && ageable.age < ageable.maximumAge) {
            devLog("Crop not fully grown, skipping regen")
            return
        }
    }

    // 镐等级检查
    if (template.minLevel > 0) {
        val heldLevel = PickaxeLevelManager.getHeldItemLevel(player)
        if (heldLevel < template.minLevel) {
            template.agents?.runAgent("onMineDeny", mapOf("event" to e, "block" to e.block), player)
            player.sendMessage("<red>需要 ${PickaxeLevelManager.getLevelDisplay(template.minLevel)}<red> 及以上的镐来挖掘这个方块<white>".toComponent())
            devLog("Pickaxe level too low: $heldLevel < ${template.minLevel}")
            e.isCancelled = true
            return
        }
    }

    if (!(template.breakCondition?.validate(mapOf("event" to e, "block" to e.block), player) ?: true)) {
        template.agents?.runAgent("onMineDeny", mapOf("event" to e, "block" to e.block), player)
        devLog("Condition not met, cancel event.")
        e.isCancelled = true
        return
    }

    // ====== 多段挖掘系统 ======
    val strength = template.strength
    if (strength > 1.0) {
        e.isCancelled = true

        // 计算工具伤害 (基础伤害 × 挖掘强度属性)
        val baseDmg = MiningPowerManager.getToolDamage(player, template.category)
        val miningPower = AttributeManager.getAttributeValue(player, "mining_power").coerceAtLeast(0.0)
        val toolDmg = baseDmg * miningPower

        // 应用伤害
        val total = MiningPowerManager.applyDamage(e.block.location, toolDmg)
        val progress = (total / strength).coerceIn(0.0, 1.0)

        devLog("Mining progress: $total / $strength (tool: $toolDmg, progress: $progress)")

        // 显示方块裂纹
        player.sendBlockDamage(e.block.location, progress.toFloat())

        // 显示行动条进度
        val pct = (progress * 100).toInt().coerceIn(0, 100)
        val bar = buildProgressBar(progress)
        player.sendActionBar(
            "<gold>挖掘进度: $bar <white>$pct% <gray>(<white>${"%.1f".format(total)}<gray>/<white>$strength<gray>)".toComponent()
        )

        // 伤害足够 → 手动破坏方块
        if (total >= strength) {
            MiningPowerManager.resetDamage(e.block.location)
            breakBlock(player, e.block, template)
        }
        return
    }

    // ====== 单次破坏（strength <= 1 的传统行为） ======
    e.isCancelled = true
    breakBlock(player, e.block, template)
}

/** 执行方块破坏逻辑 */
private fun breakBlock(player: org.bukkit.entity.Player, block: Block, template: RegenTemplate) {
    val e = BlockBreakEvent(block, player)
    MiningPowerManager.resetDamage(block.location)

    template.items.forEach {
        val item = it.build(player)
        item?.let { block.world.dropItemNaturally(block.location, it) }
    }

    val data = block.blockData.clone()

    // 播放破坏粒子/音效
    block.world.playSound(block.location, Sound.BLOCK_STONE_BREAK, 1f, 1f)
    block.world.spawnParticle(Particle.BLOCK, block.location.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, data)

    // 设置方块为空气
    block.type = Material.AIR

    // 计划刷新 — 全部在主线程执行避免竞态
    submit(delay = 1) {
        template.tempBlock?.let { blockAdapters[it.adapter]?.placeByID(block.location, it.block) }
        val time = System.currentTimeMillis() + ((template.period ?: 10.0) * 1000.0).roundToLong()
        blockRegenMap[block.location] = Regen(template.id, time, block.location, template.block, block, data)
    }

    template.agents?.runAgent("onMined", mapOf("event" to e, "block" to block), player)
}

/** 构建进度条文本 */
private fun buildProgressBar(progress: Double): String {
    val totalBars = 20
    val filled = (progress * totalBars).toInt().coerceIn(0, totalBars)
    val empty = totalBars - filled
    return "<green>${"▌".repeat(filled)}<gray>${"▌".repeat(empty)}"
}

fun getBlockID(block: Block): RegenBlock {
    if (blockAdapters.isNotEmpty()) {
        for (a in blockAdapters) {
            a.value.getID(block)?.let { return it }
        }
    }
    return MCAdapter.getID(block)
}

interface BlockAdapter {
    fun getID(block: Block): RegenBlock?
    fun place(block: Block, blockData: BlockData, location: Location, id: String)
    fun placeByID(location: Location, id: String)
}

object CEAdapter : BlockAdapter {
    override fun getID(block: Block): RegenBlock? {
        return CraftEngineBlocks.getCustomBlockState(block)?.customBlockState()?.ownerId()?.asString()?.let { RegenBlock("craftengine", it) }
    }
    override fun place(block: Block, blockData: BlockData, location: Location, id: String) {
        CraftEngineBlocks.getCustomBlockState(block)?.let { CraftEngineBlocks.place(location, it, false) }
    }
    override fun placeByID(location: Location, id: String) {
        CraftEngineBlocks.place(location, Key.of(id), false)
    }

}
object MCAdapter : BlockAdapter {
    override fun getID(block: Block): RegenBlock {
        return RegenBlock("minecraft", block.type.name)
    }
    override fun place(block: Block, blockData: BlockData, location: Location, id: String) {
        location.world.getBlockAt(location).type = Material.valueOf(id)
    }
    override fun placeByID(location: Location, id: String) {
        location.world.getBlockAt(location).type = Material.valueOf(id)
    }
}

@Awake(LifeCycle.ENABLE)
fun registerNativeBlockAdapters() {
    if (DependencyHelper.ce) blockAdapters["craftengine"] = CEAdapter
    blockAdapters["minecraft"] = MCAdapter
}


@Awake(LifeCycle.ACTIVE)
fun refreshRegenBlocks() {
    regenBlockTask = submit(period = 40) {
        if (blockRegenMap.isEmpty()) return@submit
        val now = System.currentTimeMillis()
        val expired = ArrayList<Map.Entry<Location, Regen>>()
        for (entry in blockRegenMap) {
            if (entry.value.end <= now) {
                expired.add(entry)
            }
        }
        if (expired.isNotEmpty()) {
            submit {
                for (entry in expired) {
                    devLog("Updating $entry")
                    MiningPowerManager.resetDamage(entry.key)
                    entry.value.update()
                    blockRegenMap.remove(entry.key)
                }
            }
        }
    }
}


@Awake(LifeCycle.DISABLE)
fun releaseRegenBlocks() {
    regenBlockTask?.cancel()
    for (entry in blockRegenMap) {
        devLog("Releasing $entry")
        val (_, _, location, block, bBlock, blockData) = entry.value
        blockAdapters[block.adapter]?.place(bBlock, blockData, location, block.block)
        blockRegenMap.remove(entry.key)
    }
    MiningPowerManager.blockDamage.clear()
}
