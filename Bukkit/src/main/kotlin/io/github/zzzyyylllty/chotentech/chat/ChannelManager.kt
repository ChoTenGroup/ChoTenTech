package io.github.zzzyyylllty.chotentech.chat

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ChannelManager {

    private val channels = ConcurrentHashMap<String, ChatChannel>()

    /** 默认频道 key（玩家无显式切换时使用的频道） */
    var defaultKey: String = "global"

    @Awake(LifeCycle.ENABLE)
    fun init() {
        releaseResourceFolder("channels")
        reload()
    }

    fun reload() {
        channels.clear()
        val dir = File(getDataFolder(), "channels")
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles { f -> f.isFile && f.extension.equals("yml", true) }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                runCatching {
                    val channel = ChatChannel.load(Configuration.loadFromFile(file))
                    channels[channel.key] = channel
                    infoLog("Loaded channel '${channel.key}' from ${file.name}")
                }.onFailure { e ->
                    warning("Failed to load channel from ${file.name}: $e")
                }
            }
        if (!channels.containsKey(defaultKey) && channels.isNotEmpty()) {
            defaultKey = channels.keys.first()
        }
    }

    fun get(key: String): ChatChannel? = channels[key]

    fun all(): List<ChatChannel> = channels.values.toList()

    fun defaultChannel(): ChatChannel? = channels[defaultKey]

    /**
     * 匹配快速前缀（如 ".g"）。前缀越长的频道优先匹配。
     * @return (命中的频道, 去掉前缀后的消息)；未命中返回 (null, 原文本)
     */
    fun matchQuickPrefix(text: String): Pair<ChatChannel?, String> {
        val sorted = channels.values
            .filter { it.quickPrefix.isNotEmpty() }
            .sortedByDescending { it.quickPrefix.length }
        for (channel in sorted) {
            val prefix = channel.quickPrefix
            if (text == prefix) return channel to ""
            if (text.startsWith(prefix) && text[prefix.length].isWhitespace()) {
                return channel to text.removePrefix(prefix).trimStart()
            }
        }
        return null to text
    }

    private fun infoLog(msg: String) {
        taboolib.common.platform.function.info("ChoTenTech: $msg")
    }
}
