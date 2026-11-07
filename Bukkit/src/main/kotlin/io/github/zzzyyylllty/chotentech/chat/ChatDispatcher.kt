package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.player.sendComponent
import io.github.zzzyyylllty.chotentech.team.Team
import io.github.zzzyyylllty.chotentech.team.TeamManager
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 聊天发送管线（事件路径与命令路径共用）：
 * 频道权限 -> 组队检查 -> 冷却 -> 敏感词过滤 -> 格式渲染 -> 收件人计算。
 */
object ChatDispatcher {

    /** player UUID -> (channel key -> 上次发送毫秒时间戳) */
    private val cooldowns = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()

    /**
     * 玩家退出时清掉其冷却记录。
     * cooldowns 此前无任何清理路径：每个进过服务器并说过话的玩家
     * 都会在这里留下一个内层 map（常驻内存）。服务器长期运行 +
     * 大量玩家流动时是无界增长。冷却时间戳对已离线玩家毫无意义
     * （重新登录不需要延续之前的冷却），整体移除即可。
     * 由 ChoTenTech 的玩家退出监听调用。
     */
    fun clearCooldowns(playerUuid: UUID) {
        cooldowns.remove(playerUuid)
    }

    data class Prepared(
        val sender: Player,
        val channel: ChatChannel,
        /** 提及已替换为 [@token] 标记的消息文本（渲染消息段用） */
        val messageText: String,
        val component: Component,
        val consoleLine: String,
        val recipients: List<Player>,
        val teamName: String?,
        /** @ 提及解析结果（无提及则 tokens 为空） */
        val mentions: ChatMention.Result?,
    ) {
        /** 按查看者渲染消息（提及分色：被提及者黄色高亮，其余蓝色） */
        fun componentFor(viewer: Player): Component {
            val mentions = mentions ?: return component
            if (mentions.tokens.isEmpty()) return component
            return ChatFormatter.buildForViewer(viewer, this)
        }
    }

    /**
     * 执行发送前的全部校验与渲染。
     * @return null 表示发送被拒绝（已向玩家发送提示消息）
     */
    fun prepare(player: Player, channel: ChatChannel, text: String): Prepared? {
        // 频道使用权限
        if (channel.permission.isNotEmpty() && !player.hasPermission(channel.permission)) {
            player.sendComponent("<#E08A98>你没有使用 <#EFDF8F>${channel.displayName}<#E08A98> 频道的权限。")
            return null
        }

        // 组队频道必须处于队伍中
        val team = if (channel.party) TeamManager.getTeam(player.uniqueId) else null
        if (channel.party && team == null) {
            player.sendComponent("<#E08A98>你不在队伍中，无法使用组队频道。")
            return null
        }

        // 冷却
        val cooldown = channel.effectiveCooldown(player)
        if (cooldown > 0) {
            val last = cooldowns.getOrPut(player.uniqueId) { ConcurrentHashMap() }[channel.key] ?: 0L
            val elapsedSec = (System.currentTimeMillis() - last) / 1000.0
            if (elapsedSec < cooldown) {
                player.sendComponent(
                    "<#E08A98>频道 <#EFDF8F>${channel.displayName}<#E08A98> 冷却中，请 <#EFDF8F>${"%.1f".format(cooldown - elapsedSec)}<#E08A98> 秒后再试。"
                )
                return null
            }
        }

        // 敏感词过滤
        val filtered = TabooFilter.filter(player, text)
        if (filtered.isBlank()) return null

        // 占位符替换（如 [item] → 主手物品名），结果为 MiniMessage 字符串随消息渲染
        val resolved = ChatPlaceholder.resolve(player, filtered)

        // @ 提及解析：命中处替换为 [@token] 标记，渲染阶段按查看者分色
        val mentions = ChatMention.parse(player, resolved)

        val teamName = team?.let { Bukkit.getOfflinePlayer(it.leader).name ?: "未知" }
        val (component, consoleLine) = ChatFormatter.build(player, channel, mentions.text, teamName, mentions)

        val recipients = Bukkit.getOnlinePlayers().filter { canReceive(it, player, channel, team) }

        cooldowns.getOrPut(player.uniqueId) { ConcurrentHashMap() }[channel.key] = System.currentTimeMillis()
        return Prepared(
            sender = player,
            channel = channel,
            messageText = mentions.text,
            component = component,
            consoleLine = consoleLine,
            recipients = recipients,
            teamName = teamName,
            mentions = mentions,
        )
    }

    /** 直接发送（命令路径）：按查看者渲染后发送并输出控制台行，同时提醒被提及玩家 */
    fun send(player: Player, channel: ChatChannel, text: String): Boolean {
        val prepared = prepare(player, channel, text) ?: return false
        prepared.recipients.forEach { it.sendMessage(prepared.componentFor(it)) }
        Bukkit.getConsoleSender().sendMessage(prepared.consoleLine)
        prepared.mentions?.let { ChatMention.notify(player, it) }
        return true
    }

    /**
     * 收件人判定：发送者本人始终可见；收件人屏蔽了发送者则不可见；
     * 组队频道仅队伍成员；普通频道按 radius（-1 全服 / 0 同世界 / >0 距离）。
     */
    private fun canReceive(target: Player, sender: Player, channel: ChatChannel, team: Team?): Boolean {
        if (target.uniqueId == sender.uniqueId) return true
        if (sender.uniqueId in ChatPlayerDataManager.get(target.uniqueId).ignoredPlayers) return false
        if (channel.party) {
            return team != null && target.uniqueId in team.allMembers
        }
        return when {
            channel.radius < 0 -> true
            channel.radius == 0.0 -> target.world == sender.world
            else -> target.world == sender.world &&
                target.location.distanceSquared(sender.location) <= channel.radius * channel.radius
        }
    }
}
