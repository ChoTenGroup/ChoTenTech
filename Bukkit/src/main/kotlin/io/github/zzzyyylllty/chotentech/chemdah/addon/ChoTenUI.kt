package io.github.zzzyyylllty.chotentech.chemdah.addon

import ink.ptms.chemdah.api.ChemdahAPI
import ink.ptms.chemdah.core.quest.Id
import ink.ptms.chemdah.core.quest.Option
import ink.ptms.chemdah.core.quest.QuestContainer
import ink.ptms.chemdah.core.quest.Template
import ink.ptms.chemdah.core.quest.addon.Addon
import ink.ptms.chemdah.taboolib.module.configuration.ConfigSection
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.util.asList

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

    companion object {

        /** 获取 UI 组件 */
        fun Template.chotenui() = addon<ChoTenUI>("chotenui")
    }
}

@Awake(LifeCycle.INIT)
fun registerAddons() {
    ChemdahAPI.addQuestAddon("chotenui", ChoTenUI::class.java)
}