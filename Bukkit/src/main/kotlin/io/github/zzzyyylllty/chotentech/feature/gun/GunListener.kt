package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.chotentech.util.minimessage.legacyToMiniMessage
import io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil
import net.kyori.adventure.audience.Audience
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 枪械输入监听。
 *
 * | 事件 | 行为 |
 * |---|---|
 * | 左键（空/方块） | 手持枪械 → [GunShoot.onTrigger] |
 * | 左键（点到生物） | 完好 → 开火；耐久归零 → 枪托近战（[onGunMelee]） |
 * | 右键（空/方块） | 卡壳 → 拉栓（`usage.bolt` 秒）；否则 → **按住右键瞄准**（[GunAim.pressAds]） |
 * | Q（丢弃） | 手持枪械 → 换弹（SHIFT+Q → 快速换弹），**拦掉原版丢弃** |
 * | F（交换副手） | 手持枪械 / 弹匣 → 拦掉原版交换，**打开对应菜单**（枪械菜单 / 弹匣菜单） |
 * | SHIFT+F | 手持枪械 → **切换开火模式**（原 F 的功能）；手持弹匣 → 不拦截，保持原版副手交换 |
 * | 切换物品栏 | 切到枪 → 清状态 + 出枪；切离枪 → 清状态（含还原瞄准移速与视野） |
 * | 射箭 / 发射弹射物 | 手持枪械 → 拦掉原版箭（双保险，见 [onShootBow] / [onProjectileLaunch]） |
 * | 拾取 | 弹匣带「刚被丢弃」标记且还在冷却里 → 拦下并提示 |
 * | 退出 / 死亡 / 换世界 | 清状态 |
 *
 * 枪械基材是**普通物品**（例如铁锄），不再依赖 CROSSBOW，
 * 因此右键必须 `isCancelled = true`：否则铁锄会去耕地、原版弩会装填。
 */
object GunListener {

    /** 拾取提示的最小间隔（tick）。 */
    private const val PICKUP_NOTICE_INTERVAL = 20L

    // ==================== 输入计数（诊断用） ====================

    /**
     * 实际收到的输入事件计数，`/ctt gun state` 会打印。
     *
     * ### 为什么需要
     * 玩家报「按 Q / 左右键完全没反应」时，可能有两种完全不同的原因：
     * 1. **监听器根本没注册**（taboolib 的 `@SubscribeEvent` 是类被加载时注册的，
     *    没被任何地方引用的 object 不会注册）—— 计数永远是 0；
     * 2. 事件到了但判定把它们全挡了（主手识别失败 / 被别的插件提前取消）—— 计数在涨。
     *
     * 有这几个数字，一眼就能分辨，不用再靠猜。
     */
    @Volatile
    var interactEvents: Long = 0L
        private set

    @Volatile
    var dropEvents: Long = 0L
        private set

    @Volatile
    var swapEvents: Long = 0L
        private set

    @Volatile
    var heldChangeEvents: Long = 0L
        private set

    @Volatile
    var attackEvents: Long = 0L
        private set

    // ==================== 交互（开火 / 拉栓 / 瞄准） ====================

    /** 交互诊断日志的节流（uuid → 上次输出的毫秒时间戳）。 */
    private val lastInteractTrace = ConcurrentHashMap<UUID, Long>()

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        interactEvents++
        // 副手事件一律放过（避免一次点击触发两遍）
        if (event.hand == EquipmentSlot.OFF_HAND) return

        val player = event.player
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held) ?: return
        // 先记日志（含「已被别人取消」的情况）：现在不少服务端/插件会把枪械交互整条取消掉，
        // 只在放行之后记就看不到它们了。
        traceInteract(player, event, gun)
        // 逐条说明为什么要忽略别处的 isCancelled：
        // - LEFT_CLICK_AIR / LEFT_CLICK_BLOCK = 开火，跟方块权限无关，被保护类插件取消也必须能打；
        // - RIGHT_CLICK_AIR = 瞄准，空中右键没有任何「方块权限」可言，同样必须能瞄；
        // - RIGHT_CLICK_BLOCK = 唯一保留判断的一种：保护插件挡掉的右键（上锁箱子 / 领地交互）
        //   不该被抢来当瞄准。
        if (event.isCancelled && event.action == Action.RIGHT_CLICK_BLOCK) return

        when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                // 拦住原版「左键打方块/挥空」的交互，然后开火
                event.isCancelled = true
                GunShoot.onTrigger(player, held)
            }

            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                // 拦掉原版交互（铁锄会耕地、弩会装填），右键完全由枪械系统接管
                event.isCancelled = true
                val state = GunInstance.readEnsured(held, gun) ?: return
                // 只有卡壳（jam == 1）能用右键拉栓解决；
                // 严重故障（jam == 2）必须走菜单里的「排障」（不消耗材料），再拉栓一次。
                if (state.jam >= 2) {
                    sendActionBar(player, "<#E08A98>严重故障，需要排障 <#A9B4C3>| <#EFDF8F>按 F 打开菜单")
                } else if (state.jam == 1) {
                    GunAction.bolt(player, held)
                } else if (state.durability <= 0.0) {
                    // 损坏状态不提供拉栓 / 瞄准，提示近战（实际挥击由 onBrokenGunMelee 结算）
                    sendActionBar(player, "<#E08A98>枪械已损坏 <#A9B4C3>| <#EFDF8F>左键当钝器使用")
                } else {
                    // 按住右键瞄准：客户端按住期间会持续发「使用物品」包，每次调用都是续期，
                    // 松手后由 GunAim.tickHold 的超时逻辑自动退出瞄准。
                    GunAim.pressAds(player)
                }
            }

            else -> return
        }
    }

    /**
     * 交互诊断日志（每人每秒最多一条）。
     *
     * 玩家报「左键 / 右键没反应」时，服务端日志里能直接看到：
     * 事件到底有没有到、动作是什么、有没有被别的插件提前取消、枪械识别成功没、
     * 出枪 / 读条状态是否挡着 —— 一次就能定位到是哪一层。
     */
    private fun traceInteract(player: Player, event: PlayerInteractEvent, gun: GunData) {
        val now = System.currentTimeMillis()
        val last = lastInteractTrace[player.uniqueId] ?: 0L
        if (now - last < 1000L) return
        lastInteractTrace[player.uniqueId] = now
        devLog(
            "[Gun] interact player=${player.name} action=${event.action} hand=${event.hand} " +
                "cancelled=${event.isCancelled} gun=${gun.id} " +
                "ready=${GunAim.isReady(player)} busy=${GunAction.isBusy(player)}",
        )
    }

    // ==================== 左键点到生物（开火 / 损坏近战） ====================
    /**
     * 左键**点到生物**时接管这一下。
     *
     * ### 为什么必须单独处理
     * 客户端攻击生物发的是 `INTERACT_ENTITY` + 挥臂包，**不会**触发
     * `PlayerInteractEvent`。所以只靠 [onPlayerInteract] 的话：
     * - 完好枪械对着生物左键 → 不开枪，反而用铁锄的 `attack_damage` 打出一记近战；
     * - 数据包入口 [GunPacketInput] 只接管 `AUTO` 模式的持续射击，SEMI / BURST 完全没反应。
     *
     * 这里把这一下统一接管：完好 → 转成开火；耐久归零 → 转成枪托近战。
     *
     * ### 为什么是 LOWEST
     * `AttackListener` 在 HIGH 且不检查 `isCancelled`，我们必须比它更早取消，
     * 否则它会先把这次挥击按近战再结算一遍（白扣护甲耐久 / 触发着火）。
     *
     * @see GunShoot.brokenMelee
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onGunMelee(event: EntityDamageByEntityEvent) {
        attackEvents++
        // 已经由枪械系统结算过的伤害（子弹 / 枪托近战）放行
        if (event.entity.hasMetadata(GunDamage.HIT_METADATA)) return
        val player = event.damager as? Player ?: return
        val held = player.inventory.itemInMainHand
        if (GunRegistry.gunOf(held) == null) return
        val state = GunInstance.read(held) ?: return

        if (state.durability > 0.0) {
            // 完好枪械：点到生物 = 开火。别的插件已经拦掉这一下时不越权补一枪。
            if (event.isCancelled) return
            event.isCancelled = true
            GunShoot.onTrigger(player, held)
            return
        }

        // 耐久归零：枪托近战（固定伤害，不吃 attack_damage）
        event.isCancelled = true
        GunShoot.brokenMelee(player)
    }

    // ==================== 抑制原版箭（兜底） ====================

    /**
     * 兜底 1：`EntityShootBowEvent`。
     *
     * 枪械射出的弹道完全由 [GunShoot] 生成，原版「蓄力 → 射原版箭」必须被抑制。
     * 正常路径上根本走不到这里：[onPlayerInteract] 已经把所有 `RIGHT_CLICK_*` 都
     * 拦掉了。这条是**双保险** —— 别的插件模拟右键、客户端预测包、服务端版本差异
     * 漏出来时再拦一层，并明确告诉玩家该用左键。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onShootBow(event: EntityShootBowEvent) {
        val shooter = event.entity as? Player ?: return
        // 只认事件实际发射的武器，另一手持枪不应拦截普通弓射箭。
        if (GunRegistry.gunOf(event.bow) == null) return
        event.isCancelled = true
        sendActionBar(shooter, "<#E08A98>请使用左键开火")
    }

    /**
     * 兜底 2：`ProjectileLaunchEvent`。
     *
     * 只拦**箭矢**（`AbstractArrow`）：手持枪械时不许再出原版箭。
     * 雪球 / 末影珍珠 / 鱼钩等其它弹射物**不拦**，否则玩家拿着枪连扔东西都不行了。
     * 配合上面的 [onShootBow]，两条闸门同时失效才能漏出一支原版箭。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        if (event.isCancelled) return
        if (event.entity !is AbstractArrow) return
        val shooter = event.entity.shooter as? Player ?: return
        if (GunRegistry.gunOf(shooter.inventory.itemInMainHand) == null) return
        event.isCancelled = true
    }

    // ==================== 换弹键位（Q / SHIFT+Q） ====================

    /**
     * Q = 换弹，SHIFT+Q = 快速换弹。
     *
     * 一律 `isCancelled = true`：手持枪械时 Q 不再是「丢弃物品」，
     * 否则玩家会在换弹的同时把枪扔出去。
     *
     * ### 为什么不能读 `itemInMainHand`（这条是「Q 只能把枪丢出去」的真正原因）
     * Paper 的 `ServerPlayer#drop(boolean)` 是**先** `inventory.removeFromSelected(all)`
     * 把物品从手里摘掉，**再**抛 `PlayerDropItemEvent`：
     * ```
     * ItemStack removed = inventory.removeFromSelected(all);
     * return this.drop(removed, false, true) != null;   // ← 事件在这里面才抛
     * ```
     * 所以事件里 `player.inventory.itemInMainHand` 已经是**空的** —— 用它判枪永远拿到 null，
     * 于是既不取消、也不换弹，物品正常掉出去。要判「丢的是不是枪」只能看
     * [`PlayerDropItemEvent#getItem`] 里的那个物品。
     *
     * 取消之后 Paper 会把物品放回主手（`event.isCancelled()` 分支里的 `setItemInMainHand`），
     * 但那是**事件返回之后**的事，所以换弹要延后一 tick 再起读条，否则读条第一 tick 看到空手，
     * 会被「主手物品被换掉」当场打断。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onDropItem(event: PlayerDropItemEvent) {
        dropEvents++
        val player = event.player
        val dropped = droppedStack(event)
        val gun = GunRegistry.gunOf(dropped) ?: GunRegistry.gunOf(player.inventory.itemInMainHand)
        // 进门就记：能分清「事件没到」「丢的不是枪」「主手已被摘空但掉的是枪」三种情况。
        devLog(
            "[Gun] drop-in player=${player.name} cancelled=${event.isCancelled} " +
                "dropped=${GunRegistry.gunOf(dropped)?.id ?: "null"} " +
                "held=${GunRegistry.gunOf(player.inventory.itemInMainHand)?.id ?: "null"} " +
                "sneaking=${player.isSneaking}",
        )
        if (gun == null) return
        event.isCancelled = true
        val sneaking = player.isSneaking
        submit(delay = 1, async = false) {
            if (!player.isOnline) return@submit
            // 取消后 Paper 会把枪放回主手；这里按**当时的主手**重新识别一次再起读条
            val held = player.inventory.itemInMainHand
            if (GunRegistry.gunOf(held) == null) {
                sendActionBar(player, "<#E08A98>枪不在主手，换弹已取消")
                return@submit
            }
            devLog("[Gun] drop → ${if (sneaking) "快速换弹" else "标准换弹"} (${gun.id})")
            if (sneaking) {
                GunAction.reloadFast(player, held)
            } else {
                GunAction.reloadStandard(player, held)
            }
        }
    }

    /** 事件里被丢出去的那个物品（`PlayerDropItemEvent#getItem` 可能抛错，统一兜一层）。 */
    private fun droppedStack(event: PlayerDropItemEvent): ItemStack? = try {
        // Paper 已把 getItem() 更名为 getItemDrop()，这里用新名字
        event.itemDrop.itemStack
    } catch (_: Exception) {
        null
    }

    /**
     * 丢弃事件的**最后一道闸**：有插件在我们之后把 `isCancelled` 改回 false 时再取消一次。
     *
     * `callEvent` 返回之后 Paper 才判断 `isCancelled()` 并决定是否把物品放回手里，
     * 所以 MONITOR 阶段取消依然有效。
     */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onDropItemMonitor(event: PlayerDropItemEvent) {
        if (event.isCancelled) return
        val dropped = droppedStack(event) ?: return
        if (GunRegistry.gunOf(dropped) == null) return
        event.isCancelled = true
        devLog("[Gun] drop 取消被别的插件改回，已在 MONITOR 重新取消 player=${event.player.name}")
    }

    // ==================== 菜单键位（F）/ 切换射击模式（SHIFT+F） ====================

    /**
     * F = 按手持物打开枪械菜单 / 弹匣菜单；SHIFT+F = 切换射击模式。
     *
     * 打开界面放到**下一 tick**：在 `PlayerSwapHandItemsEvent` 里直接
     * `openInventory` 容易和客户端那包的交换动作撞车（出现界面秒关）。
     *
     * 弹匣不算「占用 SHIFT+F」：手持弹匣时 SHIFT+F 交还原版副手交换，
     * 免得玩家为了换副手还要先把弹匣放下。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        swapEvents++
        val player = event.player
        val held = player.inventory.itemInMainHand
        val gun = GunRegistry.gunOf(held)
        val mag = GunRegistry.magOf(held)
        if (gun == null && mag == null) return

        if (player.isSneaking) {
            // 潜行：只有枪械占用，弹匣保持原版副手交换
            if (gun == null) return
            event.isCancelled = true
            GunAction.cycleFireMode(player, held)
            return
        }

        // 非潜行：F 一律开菜单（枪械 / 弹匣），不交换副手
        event.isCancelled = true
        submit(delay = 1, async = false) {
            if (!player.isOnline) return@submit
            // 下一 tick 重新判定主手：放包里被换掉时不要按过期的判断开界面
            val now = player.inventory.itemInMainHand
            when {
                GunRegistry.gunOf(now) != null -> GunMenu.open(player)
                GunRegistry.magOf(now) != null -> MagMenu.open(player)
            }
        }
    }

    // ==================== 自有对话框的登记作废 ====================

    /**
     * 玩家打开了**真正的容器**（箱子 / 工作台 / 熔炉……）→ 作废「我们自己弹过对话框」的登记。
     *
     * 客户端在打开别的界面时会自己关掉对话框，服务端收不到任何「对话框已关闭」的通知，
     * 所以只能靠这条事件把登记清掉；否则那个豁免会一直生效，读条就不会被真正的 GUI 打断了。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        GunDialogSessions.forget(player)
    }

    // ==================== 弹匣拾取冷却 ====================

    /**
     * 拾取提示节流：`uuid → 上次提示的 Unix 毫秒时间戳`。
     *
     * `PlayerPickupItemEvent` 在玩家站在物品上时会**每 tick** 触发一次，
     * 不节流就是每秒 20 条 ActionBar 刷屏；这里 1 秒最多提示一次。
     */
    private val pickupNoticeTick = ConcurrentHashMap<UUID, Long>()

    /**
     * 快速换弹丢出的弹匣在 100 tick（5 秒）内**任何人都**捡不起来。
     *
     * 冷却时间戳写在物品 NBT 的 `sertraline_data.mag.droppedAtMs` 上，
     * 用 Unix 毫秒比较，换世界也不会把另一个世界的 `fullTime` 混进来；
     * 插件侧只有「提示节流」这一份内存缓存，关服时由 [clearCaches] 清掉。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onPickupItem(event: PlayerPickupItemEvent) {
        val dropped = event.item
        val stack = try {
            dropped.itemStack
        } catch (_: Exception) {
            return
        }
        if (GunRegistry.magOf(stack) == null) return
        val now = System.currentTimeMillis()
        if (!MagazineCore.isPickupLocked(stack, now, GunSettings.magazine.pickupLockTicks)) return

        event.isCancelled = true
        val player = event.player
        val last = pickupNoticeTick[player.uniqueId]
        // 注意不要写成 `now - (last ?: Long.MIN_VALUE)`：那样第一次就会整数溢出，
        // 变成「永远不提示」。
        if (last != null && now - last < PICKUP_NOTICE_INTERVAL * 50L) return
        pickupNoticeTick[player.uniqueId] = now
        sendActionBar(player, "<#E08A98>弹匣刚被丢弃，无法拾取")
    }

    /** 清空拾取提示节流缓存（关服 / 卸载插件时调用）。 */
    fun clearCaches() {
        pickupNoticeTick.clear()
        lastInteractTrace.clear()
        ArmorManager.clearNotices()
        resyncGuard.clear()
        GunPrompt.clearAll()
    }

    // ==================== 切枪 ====================

    /**
     * 正在被我们主动拨回槽位的玩家。
     *
     * `setHeldItemSlot` 在 Paper 上不会回打 `PlayerItemHeldEvent`，但部分分支/服务端实现会，
     * 而那次回打会带着同一把枪再走一遍「体力不足 → 取消」，形成死循环。
     * 所以帧内 add、赋值、立刻 remove（赋值触发的监听是同步的）。
     */
    private val resyncGuard = java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        heldChangeEvents++
        if (event.isCancelled) return
        if (event.newSlot == event.previousSlot) return
        val player = event.player
        if (player.uniqueId in resyncGuard) return
        handleItemHeld(event, player)
    }

    private fun handleItemHeld(event: PlayerItemHeldEvent, player: Player) {
        val hadState = GunStateManager.existing(player) != null
        val next = try {
            player.inventory.getItem(event.newSlot)
        } catch (_: Exception) {
            null
        }
        val nextGun = if (next == null) null else GunRegistry.gunOf(next)

        // 和枪械无关的切栏（空手 ↔ 方块）直接放过，别每次都去清状态 / 改模型
        if (!hadState && nextGun == null) return

        if (nextGun != null) {
            // 出枪要扣手部体力：体力不够就**取消这次切栏**，把槽位拨回原处，
            // 而不是先切过去再反复提示「无法出枪」。判定在扣体力之前完成，不会白扣。
            if (!GunAim.canDraw(player)) {
                event.isCancelled = true
                hardResync(player, event.previousSlot)
                GunAim.notifyDrawBlockedFor(player)
                return
            }
        }

        // 切枪后后坐 / 瞄准 / 热量镜像都不该跨枪继承，先无条件清状态
        GunStateManager.clear(player)

        if (nextGun != null) {
            // 出枪（扣 draw-cost 手部体力，drawTicks 归零后才能开火）
            GunAim.beginDraw(player, nextGun)
        }
    }

    /**
     * 把客户端的选中槽位拨回 [slot]。
     *
     * 取消事件只会让**服务端**不切槽；客户端的选中格早在新物品上了，于是双方错位
     * （玩家以为拿着枪，服务端还在用旧物品结算）。所以下一 tick 主动发一次 `SetHeldItem`。
     *
     * 注意**不能**加 `heldItemSlot != slot` 这类「值相同就跳过」的判断：取消事件之后
     * 服务端的 slot 本来就还是 [slot]，那样写等于一次都不纠正，正是这个功能要修的病。
     */
    private fun hardResync(player: Player, slot: Int) {
        submit(delay = 1, async = false) {
            if (!player.isOnline) return@submit
            try {
                resyncGuard.add(player.uniqueId)
                player.inventory.heldItemSlot = slot
            } catch (_: Exception) {
            } finally {
                resyncGuard.remove(player.uniqueId)
            }
        }
    }

    // ==================== 下线 / 死亡 / 换世界 ====================

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        GunStateManager.clear(player)
        pickupNoticeTick.remove(player.uniqueId)
        ArmorManager.clearNotice(player)
        // 这两个表都是按 UUID 记的：不清就会随「历史上见过的玩家数」一直涨
        GunPrompt.forget(player)
        resyncGuard.remove(player.uniqueId)
        lastInteractTrace.remove(player.uniqueId)
        // 手部体力的「最后一次消耗时间」也是按 UUID 记的，下线一起清
        io.github.zzzyyylllty.chotentech.data.HandStamina.forget(player)
    }

    @SubscribeEvent
    fun onDeath(event: PlayerDeathEvent) {
        GunStateManager.clear(event.entity)
    }

    @SubscribeEvent
    fun onChangedWorld(event: PlayerChangedWorldEvent) {
        GunStateManager.clear(event.player)
    }

    // ==================== 文本 ====================

    private fun sendActionBar(player: Player, message: String) {
        try {
            GunPrompt.show(player, message)
        } catch (_: Exception) {
        }
    }
}
