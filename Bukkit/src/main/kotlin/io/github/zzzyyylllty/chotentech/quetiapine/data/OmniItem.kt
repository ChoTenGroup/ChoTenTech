package io.github.zzzyyylllty.chotentech.quetiapine.data

import io.github.zzzyyylllty.embiancomponent.EmbianComponent
import io.github.zzzyyylllty.chotentech.quetiapine.logger.severeL
import io.github.zzzyyylllty.chotentech.quetiapine.logger.severeS
import io.github.zzzyyylllty.chotentech.quetiapine.logger.warningL
import io.github.zzzyyylllty.chotentech.util.ExternalItemHelper
import io.github.zzzyyylllty.chotentech.quetiapine.util.VersionHelper
import io.github.zzzyyylllty.chotentech.quetiapine.util.asListEnhanced
import io.github.zzzyyylllty.chotentech.quetiapine.util.toComponent
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XItemStack
import taboolib.module.nms.NMSItemTag.Companion.asNMSCopy

private val specialItemNamespace = listOf("minecraft", "mc", "vanilla")
val componentHelper by lazy { if (VersionHelper().isOrAbove12005()) EmbianComponent.SafetyComponentSetter else null }

data class OmniItem(
    val source: String,
    val item: String,
    val parameters: LinkedHashMap<String, Any?>? = null,
    val components: LinkedHashMap<String, Any?>? = null,
) {
    fun build(player: Player?, overrideAmount: Int? = null): ItemStack {

        val amount = overrideAmount

//        val split = (if (namespaceID.contains("{")) namespaceID.parseKether(player, defaultData).split(":") else listOf("mc", "grass_block")).toMutableList()
//        val source = if (split.size >= 2) split.first().lowercase() else "mc"
//        split.removeFirst()
//        val item = split.joinToString(":")
        var itemStack: ItemStack?

        try {
            val providedItem = if (specialItemNamespace.contains(source)) {

                when (source) {
                    "mc", "minecraft", "vanilla" -> {
                        val parameters = (parameters ?: mapOf<String, Any?>()).toMutableMap()
                        parameters["material"] = item
                        XItemStack.deserialize(parameters)
                    }

                    else -> null
                }

            } else {
                if (player != null) {
                    ExternalItemHelper.build(player, source, item)
                } else {
                    ExternalItemHelper.build(player, source, item)
                }
            }
            if (providedItem == null) {
                severeL("ErrorItemGenerationFailedNull", source, item)
                return ItemStack(Material.GRASS_BLOCK)
            }
            itemStack = providedItem

        } catch (e: Throwable) {
            severeL("ErrorItemGenerationFailed", source, item)
            severeS("${e::class.simpleName}: ${e.message}")
            return ItemStack(Material.GRASS_BLOCK)
        }

        if (parameters?.isNotEmpty() ?: false) {

            val meta = itemStack.itemMeta
            parameters["name"]?.toString()?.toComponent()?.let { meta.displayName(it) }
            parameters["display-name"]?.toString()?.toComponent()?.let { meta.displayName(it) }
            parameters["custom-name"]?.toString()?.toComponent()?.let { meta.customName(it) }
            parameters["item-name"]?.toString()?.toComponent()?.let { meta.itemName(it) }
            (parameters["item-model"] ?: parameters["model"])?.toString()?.let { meta.itemModel = NamespacedKey.fromString(it) }
            itemStack.setItemMeta(meta)
            parameters["lore"].asListEnhanced()?.toComponent()?.let { itemStack.lore(it) }

        }
        if (components != null && components.isNotEmpty()) {
            if (VersionHelper().isOrAbove12005()) {
                var nmsStack = asNMSCopy(itemStack)
                components.forEach {
                    val value = it.value
                    if (value != null) componentHelper?.setComponentNMS(nmsStack, it.key, value)?.let { nmsStack = it }
                    else componentHelper?.removeComponentNMS(nmsStack, it.key)?.let { nmsStack = it }
                }
            } else {
                warningL("WarningNotSupportDataComponent")
            }
        }
        if (amount != null) {
            itemStack.amount = amount
        }
        return itemStack
    }
}