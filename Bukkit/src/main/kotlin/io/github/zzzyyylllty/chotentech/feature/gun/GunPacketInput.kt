package io.github.zzzyyylllty.chotentech.feature.gun

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import java.util.UUID

/** 左键按住/松开数据包入口。回调只采集信号，所有物品操作统一切回主线程。 */
object GunPacketInput : PacketListener {
    private var registered = false
    private var registeredListener: PacketListenerCommon? = null

    fun isRegistered(): Boolean = registered

    fun register() {
        if (registered || Bukkit.getPluginManager().getPlugin("packetevents") == null) return
        registeredListener = this.asAbstract(PacketListenerPriority.NORMAL)
        PacketEvents.getAPI().eventManager.registerListener(registeredListener!!)
        registered = true
    }

    fun shutdown() {
        registeredListener?.let { PacketEvents.getAPI().eventManager.unregisterListener(it) }
        registeredListener = null
        registered = false
    }

    override fun onPacketReceive(event: PacketReceiveEvent) {
        val uuid = event.user.uuid ?: return
        val player = Bukkit.getPlayer(uuid) ?: return
        when (event.packetType) {
            PacketType.Play.Client.PLAYER_DIGGING -> {
                // 用枚举名称而非编译期枚举常量，兼容 PacketEvents 2.10~2.14 的命名差异。
                when (WrapperPlayClientPlayerDigging(event).action.toString().uppercase()) {
                    "START_DESTROY_BLOCK", "START_DIGGING" ->
                        dispatch(player, GunShoot.TriggerSignal.PRESS)
                    "ABORT_DESTROY_BLOCK", "CANCEL_DIGGING", "RELEASE_USE_ITEM", "STOP_DESTROY_BLOCK",
                    "FINISH_DIGGING", "DROP_ITEM", "DROP_ITEM_STACK", "SWAP_ITEM_WITH_OFFHAND" ->
                        dispatch(player, GunShoot.TriggerSignal.RELEASE)
                    else -> Unit
                }
            }
            PacketType.Play.Client.ANIMATION -> dispatch(player, GunShoot.TriggerSignal.PRESS)
            PacketType.Play.Client.HELD_ITEM_CHANGE -> dispatch(player, GunShoot.TriggerSignal.RELEASE)
            else -> Unit
        }
    }

    private fun dispatch(player: Player, signal: GunShoot.TriggerSignal) {
        submit(async = false) {
            if (player.isOnline) GunShoot.onPacketTrigger(player, signal)
        }
    }
}
