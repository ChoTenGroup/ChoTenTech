package io.github.zzzyyylllty.chotentech.data.profile

import taboolib.library.xseries.XMaterial

enum class SettingType { TOGGLE, CYCLE }

data class SettingDef(
    val id: String,
    val name: String,
    val desc: String,
    val icon: XMaterial,
    val type: SettingType,
    val defaultValue: String,
    val options: List<Pair<String, String>> = emptyList(),
    val model: String? = null,
    val action: String? = null
)

/**
 * Registry of all player settings.
 * TOGGLE settings use "true"/"false"; CYCLE settings use one of [options] values.
 * Only settings actually consumed by game logic should be registered here.
 */
object SettingRegistry {

    val all: List<SettingDef> = listOf(
        SettingDef(
            id = "pvpEnabled",
            name = "PVP 模式",
            desc = "开启后可以与其他开启 PVP 的玩家战斗。\n关闭后你将无法攻击其他玩家，\n也不会被其他玩家攻击。",
            icon = XMaterial.DIAMOND_SWORD,
            type = SettingType.TOGGLE,
            defaultValue = "false",
            model = "kangelitem:void",
            action = "pvp"
        ),
        SettingDef(
            id = "soundEnabled",
            name = "界面音效",
            desc = "控制 GUI 点击、翻页等界面音效。\n关闭后所有界面操作将静音，\n但游戏内环境音效不受影响。",
            icon = XMaterial.NOTE_BLOCK,
            type = SettingType.TOGGLE,
            defaultValue = "true",
            action = "sound"
        ),
        SettingDef(
            id = "autoTrackQuest",
            name = "自动追踪任务",
            desc = "领取或接受新任务后，\n自动将该任务设为追踪状态。\n关闭后需要手动设置追踪任务。",
            icon = XMaterial.COMPASS,
            type = SettingType.TOGGLE,
            defaultValue = "true"
        ),
        SettingDef(
            id = "sideInfoMode",
            name = "右上角栏位显示",
            desc = "切换右上角信息栏位的显示模式。",
            icon = XMaterial.FILLED_MAP,
            type = SettingType.CYCLE,
            defaultValue = "quest_priority",
            options = listOf(
                "quest_priority" to "优先展示任务",
                "noclip_priority" to "优先展示切出",
                "always_noclip" to "始终显示切出点",
                "always_quest" to "始终显示追踪任务"
            )
        ),
        SettingDef(
            id = "scoreboardEnabled",
            name = "计分板",
            desc = "开关右侧计分板显示。\n关闭后计分板将完全隐藏，\n再次开启即可恢复显示。",
            icon = XMaterial.OAK_SIGN,
            type = SettingType.TOGGLE,
            defaultValue = "true",
            action = "scoreboard"
        ),
        SettingDef(
            id = "showQuestStatus",
            name = "任务状态信息",
            desc = "切换任务状态信息的提醒模式。",
            icon = XMaterial.WRITABLE_BOOK,
            type = SettingType.CYCLE,
            defaultValue = "show",
            options = listOf(
                "show" to "展示",
                "hide" to "隐藏",
                "sidebar" to "只在状态栏提醒",
                "chat" to "只在聊天内提醒"
            )
        ),
        SettingDef(
            id = "showCoordinates",
            name = "坐标栏",
            desc = "在屏幕左上角显示当前坐标信息，\n包含 X、Y、Z 轴坐标。",
            icon = XMaterial.COMPASS,
            type = SettingType.TOGGLE,
            defaultValue = "true"
        ),
        SettingDef(
            id = "showPersonalInfo",
            name = "个人信息",
            desc = "显示玩家的状态信息概览，\n包含您的经验条和延迟。",
            icon = XMaterial.PLAYER_HEAD,
            type = SettingType.TOGGLE,
            defaultValue = "true"
        ),
        SettingDef(
            id = "showEffects",
            name = "状态效果",
            desc = "显示您的状态效果，包含自定义状态效果。",
            icon = XMaterial.PLAYER_HEAD,
            type = SettingType.TOGGLE,
            defaultValue = "true"
        ),
        SettingDef(
            id = "showLevelInfo",
            name = "层级信息",
            desc = "显示当前所在层级的信息，\n包含层级名称、生存难度等。",
            icon = XMaterial.MAP,
            type = SettingType.TOGGLE,
            defaultValue = "true"
        ),
        SettingDef(
            id = "levelMusicMode",
            name = "层级音乐",
            desc = "切换层级背景音乐的播放方式。",
            icon = XMaterial.MUSIC_DISC_13,
            type = SettingType.CYCLE,
            defaultValue = "play",
            options = listOf(
                "play" to "播放",
                "master" to "播放且使用主音量",
                "off" to "关闭"
            )
        ),
        SettingDef(
            id = "levelMusicRepeat",
            name = "音乐重复",
            desc = "层级音乐的播放次数。",
            icon = XMaterial.MUSIC_DISC_13,
            type = SettingType.CYCLE,
            defaultValue = "loop",
            options = listOf(
                "loop" to "循环播放",
                "once" to "只播一次",
                "2" to "播放2次",
                "3" to "播放3次",
                "5" to "播放5次",
                "10" to "播放10次"
            )
        ),
        SettingDef(
            id = "levelTitleMode",
            name = "层级标题动画",
            desc = "切换进入层级时的标题动画。",
            icon = XMaterial.NAME_TAG,
            type = SettingType.CYCLE,
            defaultValue = "on",
            options = listOf(
                "on" to "有动画",
                "no_animation" to "无动画",
                "off" to "关闭"
            )
        ),
        SettingDef(
            id = "levelChatMessage",
            name = "层级进入聊天消息",
            desc = "进入层级时在聊天栏显示层级信息。\n包含层级编号、名称、作者等。",
            icon = XMaterial.BOOK,
            type = SettingType.TOGGLE,
            defaultValue = "false"
        ),
        SettingDef(
            id = "recallMessage",
            name = "存档召回提示",
            desc = "在基地存档点重生时，显示60秒内可召回\n到上个普通存档点的提示消息。",
            icon = XMaterial.ENDER_PEARL,
            type = SettingType.TOGGLE,
            defaultValue = "true"
        ),
        SettingDef(
            id = "hpAnimationSpeed",
            name = "血量动画速度",
            desc = "切换血量条平滑动画的追平速度。",
            icon = XMaterial.HEART_OF_THE_SEA,
            type = SettingType.CYCLE,
            defaultValue = "normal",
            options = listOf(
                "instant" to "即时同步",
                "fast" to "快速追平",
                "normal" to "正常",
                "slow" to "缓慢追平"
            )
        )
    )

    val byId: Map<String, SettingDef> = all.associateBy { it.id }

    fun get(id: String): SettingDef? = byId[id]
}
