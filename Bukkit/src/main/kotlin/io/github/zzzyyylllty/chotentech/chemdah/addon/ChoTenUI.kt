package io.github.zzzyyylllty.chotentech.chemdah.addon

import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.quest.Id
import ink.ptms.chemdah.core.quest.Option
import ink.ptms.chemdah.core.quest.QuestContainer
import ink.ptms.chemdah.core.quest.Template
import ink.ptms.chemdah.core.quest.addon.Addon
import ink.ptms.chemdah.taboolib.module.configuration.ConfigSection
import io.github.zzzyyylllty.chotentech.util.toBooleanTolerance
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.common.util.asList
import taboolib.library.xseries.XMaterial
import java.util.concurrent.ConcurrentHashMap

@Id("chotenui")
@Option(Option.Type.SECTION)
class ChoTenUI(root: ConfigSection, template: Template) : Addon(root, template) {

    val icon: String? = root.getString("icon")

    val cmd: String? = root.getString("custom-model-data")

    val model: String? = root.getString("item-model")

    val descriptionIndex = root["index"]?.asList()?.flatMap { it.lines() }

    val description = root["description"]?.asList()?.flatMap { it.lines() }

    val descriptionReward = root["reward"]?.asList()?.flatMap { it.lines() }

    val type = root.getString("type")

    val provider = root.getString("provider")

    val npcID = root["npc"]?.asList()?.flatMap { it.lines() }

    val restartOnDeath = root["restart-on-death"]?.toBooleanTolerance() ?: false

    companion object {

        /** 已提示过非法图标的来源, 避免菜单每次渲染都重复刷屏 */
        private val warnedIcons = ConcurrentHashMap.newKeySet<String>()

        /** 获取 UI 组件 */
        fun Template.chotenui() = addon<ChoTenUI>("chotenui")

        /**
         * 解析 chotenui 图标材质(兜底)。
         * 材质不存在时 XMaterial.valueOf 会抛 No enum constant 异常(如 XMaterial.MOUNTAIN),
         * 这里拦截并回退 PAPER, 同时在控制台提示一次, 避免整个菜单渲染崩溃。
         */
        fun resolveIcon(icon: String?, source: String): ItemStack {
            if (icon.isNullOrBlank()) return ItemStack(Material.PAPER)
            return try {
                XMaterial.valueOf(icon).parseItem() ?: ItemStack(Material.PAPER)
            } catch (e: IllegalArgumentException) {
                if (warnedIcons.add("$source:$icon")) {
                    warning("ChoTenUI 图标材质无效: '$icon' ($source) [${e.message}], 已回退为 PAPER")
                }
                ItemStack(Material.PAPER)
            }
        }
    }
}

@Awake(LifeCycle.INIT)
fun registerAddons() {
    ChemdahAPI.addQuestAddon("chotenui", ChoTenUI::class.java)
}