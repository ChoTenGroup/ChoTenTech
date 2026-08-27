package io.github.zzzyyylllty.chotentech.data

import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplates
import io.github.zzzyyylllty.chotentech.function.kether.parseKether
import io.github.zzzyyylllty.chotentech.listener.blockAdapters
import io.github.zzzyyylllty.chotentech.util.loc
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submit

data class Regen(
    val templateID: String,
    val end: Long,
    val location: Location,
    val block: RegenBlock,
    val bBlock: Block,
    val blockData: BlockData,
) {
    val template
    get() = regenTemplates[templateID]

    fun update() {
        submit { blockAdapters[block.adapter]?.place(bBlock, blockData, location, block.block) }
    }
}

data class RegenTemplate(
    val id: String,
    val block: RegenBlock,
    val postCondition: Condition?,
    val condition: Condition?,
    val breakCondition: Condition?,
    val period: Double?,
    val tempBlock: RegenBlock?,
    val vanilla: Boolean,
    val items: List<RegenItem>,
    val agents: Agents?,
    val minLevel: Int = 0,
    val cropType: CropType? = null,
    val strength: Double = 1.0,
    val category: BlockCategory = BlockCategory.MINERAL
)

data class RegenBlock(
    val adapter: String,
    val block: String,
)

data class RegenItem(
    val item: OmniItem,
    val amount: Any?,
    val condition: Condition?
) {
    fun build(player: Player): ItemStack? {
        val nAmount = if (amount is Int || amount is Double) amount.toInt() else amount.toString().parseKether(player)
        return if (condition?.validate(mapOf("item" to item, "amount" to nAmount), player) ?: true) item.build(player) else null
    }
}

/** 作物类型及最大生长阶段 */
enum class CropType(val blockName: String, val maxAge: Int) {
    WHEAT("wheat", 7),
    CARROTS("carrots", 7),
    POTATOES("potatoes", 7),
    BEETROOTS("beetroots", 3),
    NETHER_WART("nether_wart", 3),
    PITCHER_CROP("pitcher_crop", 4),
    TORCHFLOWER_CROP("torchflower_crop", 1),
    SWEET_BERRY_BUSH("sweet_berry_bush", 3);

    companion object {
        fun fromString(s: String): CropType? = entries.find {
            it.name.equals(s, ignoreCase = true) || it.blockName.equals(s, ignoreCase = true)
        }
    }
}