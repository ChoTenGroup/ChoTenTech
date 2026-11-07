package io.github.zzzyyylllty.chotentech.chat

import io.github.zzzyyylllty.chotentech.ChoTenTech.chatSettings
import io.github.zzzyyylllty.chotentech.ChoTenTech.levelsList
import io.github.zzzyyylllty.chotentech.ChoTenTech.playerRegions
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import java.util.UUID

/**
 * 聊天 @ 提及系统。
 *
 * 语法（@ 后不允许空格，按玩家名/关键字/层级参数整体匹配）：
 *  - `@玩家名`                    — 提及单个在线玩家（权限 chotentech.mention.player，默认授予玩家）
 *  - `@everyone` / `@all` / `@game` — 提及全体成员（三者当前同义；权限分别为
 *    chotentech.mention.everyone / mention.all / mention.game，便于日后区分）
 *  - `@level:<数字>`              — 提及该层级上的所有玩家（levelsList key 尾号或完整 key 均可）
 *  - `@level:current`             — 提及与发送者当前所在层级相同的所有玩家
 *  - `@level:*`                   — 提及所有层级上的玩家（权限 chotentech.mention.level）
 *
 * 渲染规则（按查看者是否被提及分色，MiniMessage 串逐字符按需求原文）：
 *  - 被提及者看到：`<yellow><green>@</green>PLAYERNAME</yellow>`
 *  - 未被提及者看到：`<#66ccff><yellow>@</green>PLAYERNAME</#66ccff>`
 * （`</green>` 为需求原文，非笔误。）
 *
 * 实现方式：解析阶段把成功命中的提及替换为占位标记 `[@token]`（token 为 @ 后原文），
 * 渲染阶段按查看者把标记还原为分色组件。消息文本其余部分继续走普通消息解析。
 */
object ChatMention {

    /** 提及解析结果 */
    data class Result(
        /** 替换后的消息文本：成功命中的提及替换为 `[@token]` 标记 */
        val text: String,
        /** 命中的 token（@ 后原文：玩家名 / everyone / all / game / level:x） */
        val tokens: List<String>,
        /** 被提及的玩家（去重，含发送者本人时也会被记录，提醒阶段自行排除） */
        val players: Set<UUID>,
        /** 是否为全员级提及（everyone/all/game/level:*，提醒时通知全服） */
        val mentionAll: Boolean,
    )

    private const val LEVEL_PERMISSION = "chotentech.mention.level"

    /** 全员关键字（当前同义，均为全体成员） */
    private val everyoneKeywords = setOf("everyone", "all", "game")

    /** 每个成功命中的提及对应的玩家与是否全员；canonicalToken 把 @level:current 落到具体层级 key（渲染阶段已无发送者上下文） */
    private data class Hit(val players: Set<UUID>, val mentionAll: Boolean, val canonicalToken: String? = null)

    /**
     * 扫描消息文本并解析提及。
     * 未命中任何提及规则（无权限/无此玩家/无此层级）的 @片段保持原文原样显示。
     */
    fun parse(sender: Player, text: String): Result {
        val pattern = Regex("@([A-Za-z0-9_:\\*]+)")
        val tokens = linkedSetOf<String>()
        val players = linkedSetOf<UUID>()
        var mentionAll = false

        val replaced = pattern.replace(text) { match ->
            val token = match.groupValues[1]
            val hit = resolve(sender, token) ?: return@replace match.value
            players += hit.players
            if (hit.mentionAll) mentionAll = true
            tokens += hit.canonicalToken ?: token
            "[@${hit.canonicalToken ?: token}]"
        }
        return Result(replaced, tokens.toList(), players, mentionAll)
    }

    /** 解析单个 token：命中返回其玩家集合与是否全员（canonicalToken 供渲染复用）；未命中（含无权限）返回 null */
    private fun resolve(sender: Player, token: String): Hit? {
        if (token in everyoneKeywords) {
            if (!sender.hasPermission("chotentech.mention.$token")) return null
            return Hit(Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet(), true)
        }
        if (token.startsWith("level:")) {
            if (!sender.hasPermission(LEVEL_PERMISSION)) return null
            val arg = token.substringAfter(':')
            val levelKey = when (arg) {
                // current 落到发送者当前层级的具体 key，供渲染阶段按查看者分色
                "current" -> sender.playerLevelKey() ?: return null
                "*" -> return Hit(Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet(), true)
                else -> levelsList.matchKey(arg) ?: return null
            }
            val hits = Bukkit.getOnlinePlayers()
                .filter { it.playerLevelKey() == levelKey }
                .map { it.uniqueId }
            return if (hits.isEmpty()) null else Hit(hits.toSet(), false, canonicalToken = "level:$levelKey")
        }
        if (!sender.hasPermission("chotentech.mention.player")) return null
        val target = Bukkit.getPlayerExact(token) ?: return null
        return Hit(setOf(target.uniqueId), false)
    }

    /** levelsList key 匹配：完整 key 或尾号（level-1 / level_1 / 1 均可匹配 level-1） */
    private fun Map<String, *>.matchKey(arg: String): String? =
        keys.firstOrNull { it.equals(arg, ignoreCase = true) }
            ?: keys.firstOrNull { it.endsWith("-$arg", ignoreCase = true) || it.endsWith("_$arg", ignoreCase = true) }

    /** 玩家当前所在层级 key（playerRegions → levelsList，与 getPlayerLevel 同源） */
    private fun Player.playerLevelKey(): String? {
        val regions = playerRegions[uniqueId.toString()] ?: return null
        return regions.firstOrNull { it in levelsList }
    }

    // ---------------------------------------------------------------------
    // 渲染
    // ---------------------------------------------------------------------

    /**
     * 将解析后的消息文本（含 `[@token]` 标记）渲染为组件。
     * - 非提及文本段一律按 [sender] 的颜色权限解析（消息内容由发送者权限决定，与查看者无关）；
     * - 提及标记按 [viewer] 是否被命中分色（被提及者黄色高亮，其余蓝色）。
     * 无任何提及标记时退回 [ChatFormatter.parseMessage] 普通渲染。
     */
    fun render(sender: Player, viewer: Player, text: String): net.kyori.adventure.text.Component {
        if ("[@" !in text) return ChatFormatter.parseMessage(sender, text)
        val builder = net.kyori.adventure.text.Component.text()
        var last = 0
        Regex("\\[@([A-Za-z0-9_:\\*]+)]").findAll(text).forEach { match ->
            builder.append(ChatFormatter.parseMessage(sender, text.substring(last, match.range.first)))
            builder.append(mmDeserialize(renderFor(viewer, match.groupValues[1])))
            last = match.range.last + 1
        }
        builder.append(ChatFormatter.parseMessage(sender, text.substring(last)))
        return builder.build()
    }

    /** 单个提及的渲染串（逐字符按需求原文；`</green>` 非笔误） */
    private fun renderFor(viewer: Player, token: String): String {
        val hit = isHit(viewer, token)
        val name = displayName(token)
        return if (hit) "<yellow><green>@</green>$name</yellow>"
        else "<#66ccff><yellow>@</green>$name</#66ccff>"
    }

    /** 提及的显示文本：玩家名（原样 token，客户端显示在线名）、everyone 等关键字、层级名 */
    private fun displayName(token: String): String {
        if (token in everyoneKeywords) return token
        if (token.startsWith("level:")) {
            val arg = token.substringAfter(':')
            if (arg == "*") return token
            // current 已规范化为具体 key；防御性兜底原样显示
            if (arg == "current") return token
            return levelsList.matchKey(arg)?.let { levelsList[it] as? io.github.zzzyyylllty.chotentech.data.Level }?.name ?: token
        }
        return token
    }

    /** 查看者是否被该 token 命中（决定分色）。token 为 parse 阶段规范化后的 canonical token */
    private fun isHit(viewer: Player, token: String): Boolean {
        if (token in everyoneKeywords) return true
        if (token.startsWith("level:")) {
            val arg = token.substringAfter(':')
            if (arg == "*") return true
            // current 在 parse 阶段已规范化为具体层级 key，正常不会到达这里；兜底视为命中
            if (arg == "current") return true
            val key = levelsList.matchKey(arg) ?: return false
            return viewer.playerLevelKey() == key
        }
        return viewer.name.equals(token, ignoreCase = true)
    }

    /** 解析提及渲染串（提及串由插件生成、内容可控，直接 MiniMessage 反序列化） */
    private fun mmDeserialize(mini: String): net.kyori.adventure.text.Component =
        io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil.deserialize(mini)

    // ---------------------------------------------------------------------
    // 提醒
    // ---------------------------------------------------------------------

    /** 提醒被提及的玩家：聊天栏提示 + 音效（主线程执行；全服级提及通知除发送者外的所有人） */
    fun notify(sender: Player, result: Result) {
        if (result.tokens.isEmpty()) return
        val message = chatSettings.getString(
            "mention.notify-message",
            "<#EFDF8F>{sender} <#A9B4C3>在聊天中提到了你！"
        ) ?: return
        val sound = chatSettings.getString("mention.notify-sound", "minecraft:entity.experience_orb_pickup")
        val soundPitch = chatSettings.getDouble("mention.notify-sound-pitch", 1.5)
        val targets: Collection<Player> = if (result.mentionAll) {
            Bukkit.getOnlinePlayers().filter { it.uniqueId != sender.uniqueId }
        } else {
            result.players.mapNotNull { Bukkit.getPlayer(it) }
        }
        targets.forEach { target ->
            submit {
                runCatching {
                    target.sendMessage(
                        io.github.zzzyyylllty.chotentech.util.minimessage.mmUtil.deserialize(
                            message.replace("{sender}", sender.name)
                        )
                    )
                    if (!sound.isNullOrBlank()) {
                        target.playSound(
                            net.kyori.adventure.sound.Sound.sound(
                                net.kyori.adventure.key.Key.key(sound),
                                net.kyori.adventure.sound.Sound.Source.MASTER,
                                1f,
                                soundPitch.toFloat()
                            )
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 聊天框 TAB 补全
    // ---------------------------------------------------------------------

    /** 聊天框 TAB 补全候选（以 @ 开头：在线玩家名 + 关键字 + 层级列表），随玩家加入/退出刷新 */
    @Volatile
    private var completionsCache: List<String> = emptyList()

    /** 重建候选并推送给所有在线玩家 */
    fun refreshCompletions() {
        completionsCache = buildList {
            Bukkit.getOnlinePlayers().forEach { add("@${it.name}") }
            add("@everyone")
            add("@all")
            add("@game")
            levelsList.keys.forEach { add("@level:$it") }
            add("@level:current")
            add("@level:*")
        }
        Bukkit.getOnlinePlayers().forEach { it.addCompletions(completionsCache) }
    }

    /** 当前候选（用于玩家加入时推送） */
    fun completions(): List<String> = completionsCache

    /** 推送候选到客户端聊天框（Paper 1.19.3+ 的 Player#addAdditionalChatCompletions） */
    fun Player.addCompletions(list: List<String>) {
        addAdditionalChatCompletions(list)
    }
}
