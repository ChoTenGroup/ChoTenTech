package io.github.zzzyyylllty.chotentech.chemdah.theme

import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.api.ChemdahAPI.conversationSession
import ink.ptms.chemdah.api.event.collect.ConversationEvents
import ink.ptms.chemdah.core.conversation.ConversationManager
import ink.ptms.chemdah.core.conversation.PlayerReply
import ink.ptms.chemdah.core.conversation.Session
import ink.ptms.chemdah.core.conversation.theme.Theme
import ink.ptms.chemdah.core.conversation.theme.ThemeChat.npcTalk
import ink.ptms.chemdah.core.conversation.theme.ThemeSettings
import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection
import ink.ptms.chemdah.util.thenTrue
import io.github.zzzyyylllty.chotentech.player.sendComponent
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.potion.PotionEffectType
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.floodgate.api.FloodgateApi
import org.geysermc.floodgate.api.event.FloodgateEventBus
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.library.xseries.XSound
import taboolib.module.chat.colored
import taboolib.module.configuration.Configuration
import java.util.concurrent.CompletableFuture

@SubscribeEvent
fun test(e: ConversationEvents.Pre) {
    if (FloodgateApi.getInstance().isFloodgatePlayer(e.session.player.uniqueId)) {
        e.conversation.option.theme = "skyline"
    }
}

/**
 * Chemdah
 * ink.ptms.chemdah.core.conversation.ThemeChatSettings
 *
 * @author sky
 * @since 2021/2/12 2:08 上午
 */
class ThemeSkylineSettings(root: ConfigurationSection) : ThemeSettings(root) {

    /** 对话格式 **/
    val format = root.getStringList("format")

    /** 对话单行格式 **/
    val formatLineSingle = root.getString("format-line.single")

    /** NPC 正在发言时显示回复内容 **/
    val talking = root.getString("talking", "")!!.colored()

    /** 是否启用动画 **/
    val animation = root.getBoolean("animation", true)

    /** 动画播放速度 **/
    val speed = root.getLong("speed", 1)

    /** 是否在回复上以悬浮字形式显示回复文本 **/
    val hoverText = root.getBoolean("hover-text", true)

    /** 空行 **/
    val spaceLine = root.getInt("space-line", 30)

    /** 是否启用滚轮切换回复 **/
    val useScroll = root.getBoolean("use-scroll")

    /** 音效 **/
    val selectSound: XSound? = XSound.matchXSound(root.getString("select.sound.name").toString()).orElse(null)
    val selectSoundPitch = root.getDouble("select.sound.p").toFloat()
    val selectSoundVolume = root.getDouble("select.sound.v").toFloat()

    /** 是否启用单行回复 **/
    val singleLineEnable = root.getBoolean("single-line.enable")

    /** 单行回复自动换行长度 **/
    val singleLineAutoSwap = root.getInt("single-line.auto-swap", 12)

    /** 单行回复每个回复之间的间隔文本 **/
    val singleLineReplySeparator = root.getString("single-line.reply-separator", " ")!!

    /** 自动空行填充 **/
    val spaceFilling = root.getInt("space-filling", 5)

    fun playSelectSound(session: Session) {
        selectSound?.play(session.player, selectSoundPitch, selectSoundVolume)
    }

    /**
     * 回复格式
     */
    class ReplyFormat(val root: ConfigurationSection) {

        /** 位于当前选项 **/
        val select = root.getString("1", "")!!.colored()

        /** 位于其他选项 **/
        val other = root.getString("0", "")!!.colored()

        override fun toString(): String {
            return "ReplyFormat(select='$select', other='$other')"
        }
    }

    companion object {

        val defaultReplySection = Configuration.empty()

        init {
            defaultReplySection["1"] = "&7▶ &f&n{player_side}&r &8(&6&lF&8)"
            defaultReplySection["0"] = "&8▶ &7{player_side}&r   &r"
        }
    }
}

@Awake(LifeCycle.ENABLE)

fun registerSkyline() {
    ThemeSkyline.register("skyline")
}
object ThemeSkyline : Theme<ThemeSkylineSettings>() {


    override fun allowFarewell(): Boolean {
        return false
    }
    /**
     * 初始化配置文件，并将其指向 plugins/Chemdah/core/conversation.yml 文件
     */
    override fun createConfig(): ThemeSkylineSettings {
        return ThemeSkylineSettings(ConversationManager.conf.getConfigurationSection("theme-skyline")!!)
    }


    override fun onDisplay(
        session: Session,
        message: List<String>,
        canReply: Boolean,
    ): CompletableFuture<Void> {
        // 使用 Chemdah 提供的扩展方法创建展示逻辑
        return session.createDisplay { replies ->

            session.player.showConversation(session, message, canReply, replies)
        }
    }

    override fun onClose(session: Session): CompletableFuture<Void> {
        session.player.playSound(session.player, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 1f, 0.5f)
        session.player.removePotionEffect(PotionEffectType.BLINDNESS)
        return CompletableFuture.completedFuture(null)
    }

    override fun onReset(session: Session): CompletableFuture<Void> {
        session.player.removePotionEffect(PotionEffectType.BLINDNESS)
        return CompletableFuture.completedFuture(null)
    }
}

fun Player.showConversation(
    session: Session,
    message: List<String>,
    canReply: Boolean,
    replies: List<PlayerReply>,
) {
    val player = this
    if (FloodgateApi.getInstance().isFloodgatePlayer(player.uniqueId)) {
        val fplayer = FloodgateApi.getInstance().getPlayer(player.uniqueId)
        var fmessage = "&b正在与 &3「 &7${session.title}&r &3」 &b对话...\n\n"
        session.npcSide.forEachIndexed { index, npc ->
            fmessage += when {
                // 只有一行对话
                session.npcSide.size == 1 ->
                    "&f「 &7$npc&r &f」\n"

                // 第一行
                index == 0 ->
                    "&f「 &7$npc&r &8」\n"

                // 最后一行
                index == session.npcSide.lastIndex ->
                    "&8「 &7$npc&r &f」\n"

                // 中间行
                else ->
                    "&8「 &7$npc&r &8」\n"
            }
        }
        var form = SimpleForm.builder()
            .title(session.title)
            .content(fmessage.colored())
//        .input("geyser.auth.login.form.details.pass", "123456", "")
//        .closedOrInvalidResultHandler { player.showBEConversation(session, message, canReply) }
//        .validResultHandleR { response -> session.authenticate(response.next(), response.next()) }
        val idMap = HashMap<Int, PlayerReply>()
        var id = 0
        for (reply in replies) {
            form = form.button("§1§l" + reply.build(session).colored())
            idMap[id] = reply
            id += 1
        }
        form
            .invalidResultHandler(Runnable {
                player.showConversation(session, message, canReply, replies)
            })
            .closedResultHandler(Runnable {
                session.close(refuse = true)
            })
            .validResultHandler { response ->
                val id = response.clickedButtonId()
//                player.performCommand("session reply " + idMap[id]!!.rid.toString())
                val reply = idMap[id]!!
                val bool = reply.check(session).get() ?: true
                if (bool){
                    session.player.playSound(session.player, Sound.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF, 1f, 0.5f)
                    reply.select(session)
                }
            }
        fplayer.sendForm(form) // or #sendForm(formBuilder)
    }
}