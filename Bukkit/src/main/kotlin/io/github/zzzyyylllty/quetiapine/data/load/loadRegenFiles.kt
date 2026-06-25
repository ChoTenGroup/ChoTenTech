package io.github.zzzyyylllty.quetiapine.data.load


// import org.yaml.snakeyaml.Yaml
import io.github.zzzyyylllty.chotentech.ChoTenTech.config
import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplates
import io.github.zzzyyylllty.chotentech.ChoTenTech.regenTemplatesByBlock
import io.github.zzzyyylllty.quetiapine.data.Agents
import io.github.zzzyyylllty.quetiapine.data.BlockCategory
import io.github.zzzyyylllty.quetiapine.data.Condition
import io.github.zzzyyylllty.quetiapine.data.MiningPowerManager
import io.github.zzzyyylllty.quetiapine.data.OmniItem
import io.github.zzzyyylllty.quetiapine.data.CropType
import io.github.zzzyyylllty.quetiapine.data.RegenBlock
import io.github.zzzyyylllty.quetiapine.data.RegenItem
import io.github.zzzyyylllty.quetiapine.data.RegenTemplate
import io.github.zzzyyylllty.quetiapine.logger.infoL
import io.github.zzzyyylllty.quetiapine.logger.severeS
import io.github.zzzyyylllty.quetiapine.logger.warningL
import io.github.zzzyyylllty.quetiapine.util.ExternalItemHelper
import io.github.zzzyyylllty.quetiapine.util.devLog
import io.github.zzzyyylllty.quetiapine.util.toBooleanTolerance
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common5.compileJS
import java.io.File
import kotlin.String
import kotlin.collections.forEach
import kotlin.collections.set

fun loadRegenFiles() {
    infoL("RegenBlockLoad")
    PickaxeLevelManager.load()
    MiningPowerManager.load()
    ensureDefaultRegenFiles()
    val files = File(getDataFolder(), "regen").listFiles()
    for (file in files) {
        // If directory load file in it...
        if (file.isDirectory) file.listFiles()?.forEach {
            loadRegenFile(it)
        }
        else loadRegenFile(file)
    }
}
private fun ensureDefaultRegenFiles() {
    val regenDir = File(getDataFolder(), "regen")
    if (!regenDir.exists()) {
        regenDir.mkdirs()
    }
    val defaults = listOf("regen/ores.yml", "regen/logs.yml", "regen/crops.yml")
    for (path in defaults) {
        val target = File(getDataFolder(), path)
        if (!target.exists()) {
            releaseResourceFile(path)
        }
    }
}

fun loadRegenFile(file: File) {
    devLog("Loading file ${file.name}")

    if (file.isDirectory) file.listFiles()?.forEach {
        loadRegenFile(it)
    } else {
        if (!checkRegexMatch(file.name, (config["file-load.regen"] ?: ".*").toString())) {
            devLog("${file.name} not match regex, skipping...")
            return
        }
        val map = multiExtensionLoader(file)
        if (map != null) for (it in map.entries) {
            val key = it.key
            val value = map[key] ?: continue
            (value as Map<String, Any?>?)?.let { arg -> loadRegen(key, arg) }
        } else {
            devLog("Map is null, skipping.")
        }
    }
}

fun loadRegen(key: String, arg: Map<String, Any?>) {
    val c = ConfigUtil

    val block = c.getBlock(arg["block"])
    val tempBlock = c.getBlock(arg["temp-block"])
    val post = c.getConditions(arg["post-conditions"] ?: arg["post-condition"])
    val condition = c.getConditions(arg["conditions"] ?: arg["condition"])
    val period = arg["period"].toString().toDoubleOrNull() ?: 10.0
    val vanilla = (arg["vanilla-drops"] ?: arg["vanilla"])?.toBooleanTolerance() ?: true
    val minLevel = (arg["min-level"] ?: arg["min_level"])?.toString()?.toIntOrNull() ?: 0
    val cropType = arg["crop-type"]?.toString()?.let { CropType.fromString(it) }
        ?: arg["crop_type"]?.toString()?.let { CropType.fromString(it) }
        ?: arg["crop"]?.toString()?.let { CropType.fromString(it) }
    val strength = arg["strength"]?.toString()?.toDoubleOrNull() ?: 1.0
    val category = arg["category"]?.toString()?.let { BlockCategory.fromString(it) } ?: BlockCategory.MINERAL
    val rawItems = (arg["items"] ?: arg["item"]) as? List<Any?>? ?: emptyList()
    val items = mutableListOf<RegenItem>()
    for (rawItem in rawItems) {
        if (rawItem is String) {
            items.add(RegenItem(c.getItem(rawItem) ?: OmniItem("minecraft", "DIAMOND"), 1, null))
        } else if (rawItem is Map<*,*>) {
            items.add(RegenItem(c.getItem(rawItem["item"]) ?: OmniItem("minecraft", "DIAMOND"), rawItem["amount"], c.getConditions(rawItem["conditions"] ?: rawItem["condition"])))
        }
    }

    val breakCondition = c.getConditions(arg["break-conditions"] ?: arg["break-condition"])

    if (block == null) severeS("Target Regen Block cannot be null!")
    block?.let {
        val template = RegenTemplate(
            id = key,
            block = it,
            postCondition = post,
            condition = condition,
            breakCondition = breakCondition,
            period = period,
            tempBlock = tempBlock,
            vanilla = vanilla,
            items = items,
            agents = c.getAgents(arg),
            minLevel = minLevel,
            cropType = cropType,
            strength = strength,
            category = category
        )
        val bTemplate = regenTemplatesByBlock[block]
        if (bTemplate != null) {
            regenTemplatesByBlock[block]?.add(key)
        } else {
            regenTemplatesByBlock[block] = mutableListOf(key)
        }
        regenTemplates[key] = template
    }
}
