package io.github.zzzyyylllty.chotentech.attribute.crossbow

import io.github.zzzyyylllty.chotentech.data.ChoTenDataHelper
import org.bukkit.Material
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 弩箭机制事件监听器
 * 处理：右键打开箭矢夹、弩发射箭矢、飞行箭矢清理
 * 注意：箭矢命中穿透计算在 AttackListener 中完成
 */
object CrossbowListener {

    // ============ 箭矢夹GUI ============

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!CrossbowManager.isEnabled()) return

        val action = event.action
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
        ) return

        val item = event.item ?: return
        if (!CrossbowManager.isArrowMagazine(item)) return

        event.isCancelled = true

        val player = event.player
        val slot = when (event.hand) {
            EquipmentSlot.HAND -> player.inventory.heldItemSlot
            EquipmentSlot.OFF_HAND -> 40
            else -> return
        }

        val magazine = player.inventory.getItem(slot) ?: return
        ArrowMagazineGUI.open(player, magazine, slot)
    }

    // ============ 弩发射箭矢 ============

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        if (!CrossbowManager.isEnabled()) return
        if (event.isCancelled) return

        val projectile = event.entity
        if (projectile !is AbstractArrow) return

        val shooter = projectile.shooter
        if (shooter !is Player) return

        val player = shooter

        // 检查是否为弩发射
        val weapon = try {
            projectile.weapon
        } catch (_: NoSuchMethodError) {
            player.inventory.itemInMainHand
        }

        if (weapon == null || weapon.type != Material.CROSSBOW) return

        // 查找玩家背包中的箭矢夹
        val magazineSlot = ArrowMagazineGUI.findMagazineSlot(player)
        if (magazineSlot == -1) return

        val magazine = when {
            magazineSlot in 0..35 -> player.inventory.getItem(magazineSlot)
            magazineSlot == 40 -> player.inventory.itemInOffHand.takeIf { it.type != Material.AIR }
            else -> null
        } ?: return

        // 获取第一支箭矢
        val firstArrow = CrossbowManager.getFirstArrow(magazine) ?: return
        val arrowData = CrossbowManager.getArrowDataFromItem(firstArrow) ?: return

        // 应用箭矢速度
        val velocityMultiplier = arrowData.velocity.coerceAtLeast(0.1)
        val originalVelocity = projectile.velocity
        projectile.velocity = originalVelocity.multiply(Vector(velocityMultiplier, velocityMultiplier, velocityMultiplier))

        // 设置箭矢伤害
        projectile.damage = arrowData.damage

        // 注册飞行中箭矢数据（AttackListener 会在命中时读取）
        CrossbowManager.registerFlyingArrow(projectile.uniqueId, arrowData)

        // 消耗箭矢夹中的第一支箭
        val newMag = CrossbowManager.consumeFirstArrow(magazine)
        player.inventory.setItem(magazineSlot, newMag)
    }

    // ============ 清理飞行箭矢记录 ============

    @SubscribeEvent
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity is AbstractArrow) {
            CrossbowManager.removeFlyingArrow(event.entity.uniqueId)
        }
    }

    @SubscribeEvent
    fun onEntityRemove(event: EntityRemoveEvent) {
        if (event.entity is AbstractArrow) {
            CrossbowManager.removeFlyingArrow(event.entity.uniqueId)
        }
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        CrossbowManager.invalidateDefenseCache(event.player.uniqueId)
    }

    // ============ 视角装填/检视系统 ============

    /** 玩家当前的弹匣交互状态 */
    private enum class MagInteractionState { IDLE, LOADING, LOOKING_UP }

    /** 玩家UUID -> 当前交互状态 */
    private val interactionStates = ConcurrentHashMap<UUID, MagInteractionState>()
    /** 玩家UUID -> 装填任务 */
    private val loadingTasks = ConcurrentHashMap<UUID, PlatformExecutor.PlatformTask>()
    /** 玩家UUID -> 抬头检视延时任务 */
    private val lookUpTasks = ConcurrentHashMap<UUID, PlatformExecutor.PlatformTask>()

    /** 低头装填的俯仰角度阈值 */
    private const val PITCH_LOOK_DOWN = 60.0
    /** 抬头检视的俯仰角度阈值 */
    private const val PITCH_LOOK_UP = -60.0

    /**
     * 切换快捷栏到弹匣时提示操作说明
     */
    @SubscribeEvent
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        if (!CrossbowManager.isEnabled()) return

        val player = event.player
        val newSlot = event.newSlot
        val item = player.inventory.getItem(newSlot) ?: return
        if (!CrossbowManager.isArrowMagazine(item)) return

        CrossbowManager.sendMagazineHint(player)
    }

    /**
     * 处理视角装填与检视
     * - 低头（pitch >= 60°）：每秒装填1发箭矢
     * - 抬头（pitch <= -60°）保持3秒：显示弹匣状态
     *
     * 优化说明：先做俯仰角度检查，避免频繁 NBT 读取
     */
    @SubscribeEvent
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!CrossbowManager.isEnabled()) return
        if (event.isCancelled) return

        val player = event.player
        val uuid = player.uniqueId
        val pitch = player.location.pitch

        // 快速路径：俯角在阈值范围内才检查手持物品（避免无谓的 NBT 读取）
        val inThreshold = pitch >= PITCH_LOOK_DOWN || pitch <= PITCH_LOOK_UP
        if (!inThreshold) {
            cleanupPlayerInteraction(uuid)
            return
        }

        // 获取玩家手持的弹匣（涉及 NBT 读取）
        val (magazine, magazineSlot) = getHeldMagazine(player) ?: run {
            cleanupPlayerInteraction(uuid)
            return
        }

        val currentState = interactionStates[uuid] ?: MagInteractionState.IDLE

        when {
            pitch >= PITCH_LOOK_DOWN -> {
                // 低头：进入装填状态（如果尚未进入）
                if (currentState != MagInteractionState.LOADING) {
                    enterLoadingState(uuid, player, magazineSlot)
                }
                cancelLookUpTask(uuid)
            }
            pitch <= PITCH_LOOK_UP -> {
                // 抬头：进入检视状态（如果尚未进入）
                if (currentState != MagInteractionState.LOOKING_UP) {
                    enterLookUpState(uuid, player, magazine)
                }
                cancelLoadingTask(uuid)
            }
        }
    }

    /** 获取玩家手持的弹匣（优先主手） */
    private fun getHeldMagazine(player: Player): Pair<ItemStack, Int>? {
        val mainHand = player.inventory.itemInMainHand
        if (CrossbowManager.isArrowMagazine(mainHand)) {
            return mainHand to player.inventory.heldItemSlot
        }
        val offHand = player.inventory.itemInOffHand
        if (CrossbowManager.isArrowMagazine(offHand)) {
            return offHand to 40
        }
        return null
    }

    /** 进入低头装填状态，启动每秒1发的装填任务 */
    private fun enterLoadingState(uuid: UUID, player: Player, slot: Int) {
        interactionStates[uuid] = MagInteractionState.LOADING
        cancelLoadingTask(uuid)
        cancelLookUpTask(uuid)

        val task = submit(async = false, delay = 20L, period = 20L) {
            if (!player.isOnline || interactionStates[uuid] != MagInteractionState.LOADING) return@submit
            CrossbowManager.loadArrowFromInventory(player, slot)
        }
        loadingTasks[uuid] = task
    }

    /** 进入抬头检视状态，3秒后显示弹匣信息 */
    private fun enterLookUpState(uuid: UUID, player: Player, magazine: ItemStack) {
        interactionStates[uuid] = MagInteractionState.LOOKING_UP
        cancelLookUpTask(uuid)
        cancelLoadingTask(uuid)

        val task = submit(async = false, delay = 60L) {
            if (interactionStates[uuid] == MagInteractionState.LOOKING_UP && player.isOnline) {
                CrossbowManager.sendMagazineStatus(player, magazine)
            }
        }
        lookUpTasks[uuid] = task
    }

    private fun cancelLoadingTask(uuid: UUID) {
        loadingTasks.remove(uuid)?.cancel()

    }

    private fun cancelLookUpTask(uuid: UUID) {
        lookUpTasks.remove(uuid)?.cancel()
    }

    /** 清理玩家的所有交互状态 */
    private fun cleanupPlayerInteraction(uuid: UUID) {
        interactionStates.remove(uuid)
        cancelLoadingTask(uuid)
        cancelLookUpTask(uuid)
    }
}
