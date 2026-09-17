package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.chotentech.data.profile.ProfileManager
import io.github.zzzyyylllty.chotentech.gui.ProfileCreationPending
import io.github.zzzyyylllty.chotentech.gui.ProfileDeletionPending
import io.github.zzzyyylllty.chotentech.gui.ProfileSelectionGUI
import io.github.zzzyyylllty.chotentech.util.devLog
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import java.util.concurrent.ConcurrentHashMap

object ProfileListener {

    /** Players who haven't confirmed a profile selection this session (uuid -> 当前会话的 player 对象,用于防掉线重进竞态) */
    val pendingSelection = ConcurrentHashMap<String, Player>()

    /** Track players who already triggered the profile GUI this session (uuid -> player 对象) */
    private val guiTriggered = ConcurrentHashMap<String, Player>()

    /** 上次因玩家主动关闭而重开选择界面的时间:防止关闭事件连发导致的重开风暴 */
    private val lastReopenAt = ConcurrentHashMap<String, Long>()

    /** 登录流程完成标记(uuid):加载期(客户端还在加载/位置同步)发出的传送包客户端不会应用,
     *  选择必须等登录完成后由真实移动触发,否则玩家会卡在隔离点 */
    private val loginComplete = ConcurrentHashMap.newKeySet<String>()

    fun markLoginComplete(uuid: String) {
        loginComplete.add(uuid)
    }

    /** 临时开关:为 true 时移动不弹选择界面,直接使用 Default 档案(多人时选择界面异常期间的临时方案) */
    private val skipProfileSelection = true

    /** Holding location before profile selection */
    private val holdingLocation: Location by lazy {
        val world = Bukkit.getWorlds().first()
        Location(world, 0.5, 1.0, 0.5)
    }

    /** Mark player as pending — teleport to holding, clear inv, reset GUI trigger, clear active profile */
    fun setPending(player: Player) {
        val uuid = player.uniqueId.toString()
        println("[ProfileListener] ${player.name} 进入强制选择状态")
        pendingSelection[uuid] = player
        player.inventory.clear()
        player.teleport(holdingLocation)
        guiTriggered.remove(uuid)
        // Clear active profile — player must explicitly select one in the GUI before playing
        val shared = ProfileManager.getSharedData(uuid)
        shared.activeProfileId = ""
        ProfileManager.saveSharedData(uuid)
    }

    /** 关闭档案选择界面后,玩家再次移动时重新弹出 */
    fun dismissGui(player: Player) {
        guiTriggered.remove(player.uniqueId.toString())
    }

    /** Release from pending without further action — player selected a profile via GUI */
    fun releaseFromPending(player: Player) {
        val uuid = player.uniqueId.toString()
        pendingSelection.remove(uuid)
        guiTriggered.remove(uuid)
    }

    /** Confirm profile selection — release pending, restore inv, teleport to profile location */
    fun confirmSelection(player: Player) {
        val uuid = player.uniqueId.toString()
        pendingSelection.remove(uuid)
        ProfileManager.syncToSharedData(uuid, io.github.zzzyyylllty.chotentech.ChoTenTech.playerDataMap[uuid] ?: return)
        ProfileManager.saveSharedData(uuid)
        ProfileManager.applyInventory(player)
        ProfileManager.teleportToProfileLocation(player)
    }

    @SubscribeEvent
    fun onJoinClearInventory(e: PlayerJoinEvent) {
        val player = e.player
        val uuid = player.uniqueId.toString()
        val pending = pendingSelection[uuid] ?: return
        if (pending === player) {
            player.inventory.clear()
        }
    }

    /** Block X/Z movement while pending, open GUI on first move */
    

@SubscribeEvent(priority = EventPriority.NORMAL)
    fun onHoldPending(event: PlayerMoveEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val pending = pendingSelection[uuid] ?: return
        // 掉线重进时旧连接的残留事件可能晚于新连接处理,只响应当前 pending 的 player 对象
        if (pending !== player) return

        // Ignore rotation-only
        val to = event.to ?: return
        if (event.from.x == to.x && event.from.z == to.z) return

        // 隔离点(0,0)附近 1 格内的移动放行:客户端登录期的位置同步包只落在 0,0,不会累计;
        // 玩家真实移动的位移会累计,超出 1 格后进入拦截并触发选择
        if (kotlin.math.abs(to.blockX) <= 1 && kotlin.math.abs(to.blockZ) <= 1) return

        // Block X/Z movement
        event.isCancelled = true
        // 玩家已处于打开界面时不再传送/清背包:
        // 界面弹出前排队的陈旧移动事件(服务器卡顿时很常见)不应打断/关闭选择界面
        if (player.openInventory.topInventory.type == InventoryType.CRAFTING) {
            player.teleport(holdingLocation)
            player.inventory.clear()
        }

        // 登录流程未完成(客户端还在加载/位置同步)时不触发选择:
        // 此阶段发送的传送包客户端不会应用,玩家会停在隔离点
        if (!loginComplete.contains(uuid)) return

        // Open GUI on first actual movement
        if (guiTriggered.putIfAbsent(uuid, player) == null) {
            println("[ProfileListener] ${player.name} 首次移动触发选择")
            if (skipProfileSelection) {
                // 临时:不弹选择界面,直接使用 Default 档案并释放
                // 注意:档案数据读取(DB 繁忙/锁表时可能抛异常)发生在 try 外会炸掉事件处理器,
                // 且 guiTriggered 标记已置位,玩家将永远无法再次触发选择 —— 必须保证任何路径都释放
                var released = false
                try {
                    println("[ProfileListener] ${player.name} 跳过选择,自动使用 Default")
                    val shared = ProfileManager.getSharedData(uuid)
                    val infos = ProfileManager.getProfileInfos(uuid)
                    val default = infos.find { it.profileName == "Default" } ?: infos.firstOrNull()
                    if (default != null) {
                        try {
                            if (shared.activeProfileId != default.profileId) {
                                // 切换档案(内部会恢复背包并传送到档案位置)
                                val result = ProfileManager.switchProfile(player, default.profileId)
                                println("[ProfileListener] ${player.name} switchProfile 返回: ${result?.profileId ?: "null"}")
                            } else {
                                // 活动档案已是 Default:switchProfile 不会执行,补上背包恢复与传送
                                // (setPending 把玩家留在了隔离点,这里负责送往最终位置)
                                ProfileManager.applyInventory(player)
                                if (!shared.isCompletedTutorial) {
                                    ProfileManager.teleportToTutorialEntry(player)
                                } else {
                                    ProfileManager.teleportToProfileLocation(player)
                                }
                            }
                            println("[ProfileListener] ${player.name} 传送后位置 -> ${player.location.world?.name} ${player.location.blockX},${player.location.blockY},${player.location.blockZ}")
                            // 陈旧位置包回拉防护:客户端在收到传送包前从隔离点发出的移动包,
                            // 会在释放后被服务器处理,把玩家从目标位置拉回隔离点。
                            // 等 0.5s 陈旧包排空后重发一次传送,确保玩家停在目标位置
                            val settleTarget = player.location
                            submit(delay = 10) {
                                if (player.isOnline) player.teleport(settleTarget)
                            }
                        } catch (e: Exception) {
                            // 切换失败也把活动档案指向 Default,保证后续保存不写脏行
                            shared.activeProfileId = default.profileId
                            ProfileManager.saveSharedData(uuid)
                            println("[ProfileListener] ${player.name} 自动选择 Default 切换失败: ${e.message}")
                        }
                        releaseFromPending(player)
                        released = true
                        println("[ProfileListener] ${player.name} 已释放,使用档案 ${default.profileId}")
                        return
                    }
                    println("[ProfileListener] ${player.name} 未找到可用的档案,回退到界面")
                } catch (e: Exception) {
                    // 数据读取异常(DB 繁忙/锁表等)同样必须释放并清触发标记,否则玩家永久卡在选择状态
                    println("[ProfileListener] ${player.name} 自动选择数据读取失败: ${e.message}")
                }
                if (!released) {
                    releaseFromPending(player)
                    println("[ProfileListener] ${player.name} 释放(异常兜底)")
                }
                return
            }
            ProfileSelectionGUI().open(player)
        }
    }

    /** 强制选择期间禁止与世界的交互:排队的陈旧交互包不再打开其他菜单盖掉选择界面 */
    

@SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onHoldInteract(event: PlayerInteractEvent) {
        val player = event.player
        val pending = pendingSelection[player.uniqueId.toString()] ?: return
        if (pending !== player) return
        event.isCancelled = true
    }

    /** Re-open GUI when pending player presses ESC — not triggered by plugin-initiated closeInventory() */
    @SubscribeEvent
    fun onReopenGui(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId.toString()
        val pending = pendingSelection[uuid] ?: return
        if (pending !== player) return
        // 记录强制选择期间的所有关闭原因,便于定位非玩家主动的关闭来源
        println("[ProfileListener] ${player.name} 界面关闭 reason=${event.reason}")
        if (event.reason != org.bukkit.event.inventory.InventoryCloseEvent.Reason.PLAYER) return
        if (ProfileCreationPending.pending.containsKey(uuid) || ProfileDeletionPending.pending.containsKey(uuid)) return

        // 防抖:关闭事件连发时只重开一次,避免重开-关闭-重开风暴
        val now = System.currentTimeMillis()
        if (now - (lastReopenAt[uuid] ?: 0L) < 400) {
            println("[ProfileListener] ${player.name} 重开被防抖拦截 reason=${event.reason}")
            return
        }
        lastReopenAt[uuid] = now
        println("[ProfileListener] ${player.name} 关闭触发重开 reason=${event.reason}")

        submit(delay = 1) {
            if (pendingSelection[uuid] === player) {
                ProfileSelectionGUI().open(player)
            }
        }
    }

    /** Handle chat input for profile creation/deletion */
    

@SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        // 有挂起的创建/删除操作时才拦截
        if (!ProfileCreationPending.pending.containsKey(uuid) && !ProfileDeletionPending.pending.containsKey(uuid)) return
        // 取消必须在本事件线程内同步设置，否则消息已广播
        event.isCancelled = true
        val message = event.message
        // handleChat 会打开 GUI/传送/写库，需在主线程执行
        submit {
            if (!ProfileCreationPending.handleChat(player, message)) {
                ProfileDeletionPending.handleChat(player, message)
            }
        }
    }

    /** Clean up on quit */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        // 仅清理当前会话(同一 player 对象)的状态,防止掉线重进时旧连接的 quit 事件误清新连接的 pending 状态
        if (pendingSelection[uuid] === player) pendingSelection.remove(uuid)
        if (guiTriggered[uuid] === player) guiTriggered.remove(uuid)
        loginComplete.remove(uuid)
        lastReopenAt.remove(uuid)
        ProfileCreationPending.pending.remove(uuid)
        ProfileDeletionPending.pending.remove(uuid)
    }
}
