package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.data.getData
import io.github.zzzyyylllty.chotentech.util.DependencyHelper
import net.luckperms.api.LuckPerms
import net.luckperms.api.context.ContextCalculator
import net.luckperms.api.context.ContextConsumer
import net.luckperms.api.context.ImmutableContextSet
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * LuckPerms 动态上下文计算器
 *
 * 暴露玩家当前 profile 作为上下文键 `profile`，值为 profileId。
 * 实现独立 profile 权限的基础：可在 LP 权限节点中限定 context profile=<id>。
 */
object ProfileContextCalculator : ContextCalculator<Player> {

    private val luckPerms: LuckPerms? by lazy {
        if (DependencyHelper.luckPerms) {
            Bukkit.getServicesManager().load(LuckPerms::class.java)
        } else null
    }

    /** 所有已知 profile ID（用于 estimate 优化） */
    private val knownProfileIds = setOf("1", "2", "3", "4", "5")

    override fun calculate(target: Player, consumer: ContextConsumer) {
        val data = target.getData() ?: return
        val profileId = data.profileId
        consumer.accept("profile", profileId)
    }

    override fun estimatePotentialContexts(): ImmutableContextSet {
        val builder = ImmutableContextSet.builder()
        for (id in knownProfileIds) {
            builder.add("profile", id)
        }
        return builder.build()
    }

    /** 注册到 LuckPerms */
    fun register() {
        luckPerms?.contextManager?.registerCalculator(this)
    }

    /** 注销 */
    fun unregister() {
        luckPerms?.contextManager?.unregisterCalculator(this)
    }

    /** 通知 LuckPerms 某玩家的上下文已变化 */
    fun signalUpdate(player: Player) {
        luckPerms?.contextManager?.signalContextUpdate(player)
    }
}
