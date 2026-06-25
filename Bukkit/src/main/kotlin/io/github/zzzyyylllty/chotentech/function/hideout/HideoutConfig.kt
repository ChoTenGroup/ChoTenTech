package io.github.zzzyyylllty.chotentech.function.hideout

import org.bukkit.inventory.ItemStack
import taboolib.library.configuration.ConfigurationSection
import taboolib.common.platform.function.warning
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Configuration
import java.io.File

object HideoutConfig {

    val modules = linkedMapOf<String, HideoutModule>()
    val fuelTypes = linkedMapOf<String, FuelType>()
    val recipes = linkedMapOf<String, HideoutRecipe>()
    val recipeListByModule = linkedMapOf<String, MutableList<String>>()

    fun load(dataFolder: File) {
        modules.clear()
        fuelTypes.clear()
        recipes.clear()
        recipeListByModule.clear()

        // Load modules from individual files in modules/
        val modulesDir = File(dataFolder, "modules")
        if (modulesDir.isDirectory) {
            modulesDir.listFiles { f -> f.name.endsWith(".yml", ignoreCase = true) }?.forEach { file ->
                val id = file.nameWithoutExtension
                try {
                    loadSingleModule(id, Configuration.loadFromFile(file))
                } catch (e: Exception) {
                    warning("加载模块 $id 失败: ${e.message}")
                }
            }
        }

        // Load fuel_types.yml
        val fuelFile = File(dataFolder, "fuel_types.yml")
        if (fuelFile.exists()) {
            try {
                loadFuelTypes(Configuration.loadFromFile(fuelFile))
            } catch (e: Exception) {
                warning("加载燃料类型失败: ${e.message}")
            }
        }

        // Load recipes.yml
        val recipesFile = File(dataFolder, "recipes.yml")
        if (recipesFile.exists()) {
            try {
                loadRecipes(Configuration.loadFromFile(recipesFile))
            } catch (e: Exception) {
                warning("加载配方失败: ${e.message}")
            }
        }
    }

    private fun loadSingleModule(id: String, sec: ConfigurationSection) {
        val levelSecs = sec.getConfigurationSection("levels") ?: return
        val levels = linkedMapOf<Int, HideoutModuleLevel>()

        for (lvStr in levelSecs.getKeys(false)) {
            val lv = lvStr.toIntOrNull() ?: continue
            val lvSec = levelSecs.getConfigurationSection(lvStr) ?: continue

            levels[lv] = HideoutModuleLevel(
                fuelCapacity = lvSec.getDouble("fuel_capacity", 0.0),
                fuelEfficiency = lvSec.getDouble("fuel_efficiency", 1.0),
                craftSpeed = lvSec.getDouble("craft_speed", 1.0),
                productionRate = lvSec.getDouble("production_rate", 0.0),
                productionItem = lvSec.getString("production_item"),
                fuelSavePercent = lvSec.getInt("fuel_save_percent", 0),
                gpuSlots = lvSec.getInt("gpu_slots", 0),
                buffDuration = lvSec.getInt("buff_duration", 0),
                buffAmplifier = lvSec.getInt("buff_amplifier", 0),
                upgradeCost = loadCostList(lvSec, "upgrade_cost"),
                currencyCost = loadCurrencyCostList(lvSec, "currency_cost"),
                upgradeTime = lvSec.getInt("upgrade_time", 0),
            )
        }

        val module = HideoutModule(
            id = id,
            name = sec.getString("name") ?: id,
            description = sec.getString("description") ?: "",
            material = sec.getString("material") ?: "STONE",
            itemModel = sec.getString("item_model"),
            slot = sec.getInt("slot", 0),
            maxLevel = sec.getInt("max_level", 1),
            usage = sec.getDouble("usage", 0.0),
            levels = levels,
        )
        modules[id] = module
    }

    private fun loadFuelTypes(section: ConfigurationSection?) {
        if (section == null) return
        for (id in section.getKeys(false)) {
            try {
                val sec = section.getConfigurationSection(id) ?: continue
                val fuel = FuelType(
                    id = id,
                    material = sec.getString("material"),
                    itemModel = sec.getString("item_model"),
                    sertralineId = sec.getString("sertraline_id"),
                    burnTime = sec.getDouble("burn_time", 0.0),
                )
                fuelTypes[id] = fuel
            } catch (e: Exception) {
                warning("加载燃料类型 $id 失败: ${e.message}")
            }
        }
    }

    private fun loadRecipes(section: ConfigurationSection?) {
        if (section == null) return
        for (id in section.getKeys(false)) {
            try {
                val sec = section.getConfigurationSection(id) ?: continue
                val recipe = HideoutRecipe(
                    id = id,
                    module = sec.getString("module") ?: continue,
                    minLevel = sec.getInt("min_level", 1),
                    craftTime = sec.getInt("craft_time", 60),
                    usageCost = sec.getDouble("usage_cost", 0.0),
                    inputs = loadItemList(sec.getConfigurationSection("input")),
                    outputs = loadItemList(sec.getConfigurationSection("output")),
                )
                recipes[id] = recipe
                recipeListByModule.getOrPut(recipe.module) { mutableListOf() } += id
            } catch (e: Exception) {
                warning("加载配方 $id 失败: ${e.message}")
            }
        }
    }

    private fun loadCostList(section: ConfigurationSection, path: String): List<ItemCost> {
        val list = mutableListOf<ItemCost>()
        for (map in section.getMapList(path)) {
            @Suppress("UNCHECKED_CAST")
            val m = map as? Map<String, Any> ?: continue
            if (m.containsKey("currency")) continue
            list += ItemCost(
                material = m["material"]?.toString(),
                sertralineId = m["sertraline_id"]?.toString(),
                amount = (m["amount"] as? Number)?.toInt() ?: 1,
            )
        }
        return list
    }

    private fun loadCurrencyCostList(section: ConfigurationSection, path: String): List<CurrencyCost> {
        val list = mutableListOf<CurrencyCost>()
        for (map in section.getMapList(path)) {
            @Suppress("UNCHECKED_CAST")
            val m = map as? Map<String, Any> ?: continue
            val currencyId = m["currency"]?.toString() ?: continue
            list += CurrencyCost(
                currency = currencyId,
                amount = (m["amount"] as? Number)?.toDouble() ?: 0.0,
            )
        }
        return list
    }

    private fun loadItemList(section: ConfigurationSection?): List<ItemRequirement> {
        val list = mutableListOf<ItemRequirement>()
        if (section == null) return list
        for (key in section.getKeys(false)) {
            val sec = section.getConfigurationSection(key) ?: continue
            list += ItemRequirement(
                material = sec.getString("material"),
                sertralineId = sec.getString("sertraline_id"),
                amount = sec.getInt("amount", 1),
            )
        }
        return list
    }

    fun getModule(id: String): HideoutModule? = modules[id]

    fun getFuelType(id: String): FuelType? = fuelTypes[id]

    fun getRecipe(id: String): HideoutRecipe? = recipes[id]

    fun getFuelTypeByItem(item: ItemStack): FuelType? {
        val sertId = io.github.zzzyyylllty.sertraline.Sertraline.api().getId(item)
        if (sertId != null) {
            fuelTypes.values.firstOrNull { it.sertralineId == sertId }?.let { return it }
        }
        val mat = item.type
        return fuelTypes.values.firstOrNull {
            it.material?.equals(mat.name, ignoreCase = true) == true
        }
    }

    fun getRecipesForModule(moduleId: String): List<HideoutRecipe> {
        return recipeListByModule[moduleId]?.mapNotNull { recipes[it] } ?: emptyList()
    }
}

data class HideoutModule(
    val id: String,
    val name: String,
    val description: String,
    val material: String,
    val itemModel: String?,
    val slot: Int,
    val maxLevel: Int,
    val usage: Double,
    val levels: Map<Int, HideoutModuleLevel>,
) {
    fun getLevelConfig(level: Int): HideoutModuleLevel? = levels[level]
}

data class HideoutModuleLevel(
    val fuelCapacity: Double,
    val fuelEfficiency: Double,
    val craftSpeed: Double,
    val productionRate: Double,
    val productionItem: String?,
    val fuelSavePercent: Int,
    val gpuSlots: Int,
    val buffDuration: Int,
    val buffAmplifier: Int,
    val upgradeCost: List<ItemCost>,
    val currencyCost: List<CurrencyCost>,
    val upgradeTime: Int = 0,
)

data class FuelType(
    val id: String,
    val material: String?,
    val itemModel: String?,
    val sertralineId: String?,
    val burnTime: Double,
)

data class HideoutRecipe(
    val id: String,
    val module: String,
    val minLevel: Int,
    val craftTime: Int,
    val usageCost: Double,
    val inputs: List<ItemRequirement>,
    val outputs: List<ItemRequirement>,
)

data class ItemCost(
    val material: String?,
    val sertralineId: String?,
    val amount: Int,
)

data class CurrencyCost(
    val currency: String,
    val amount: Double,
)

data class ItemRequirement(
    val material: String?,
    val sertralineId: String?,
    val amount: Int,
) {
    fun toItemStack(): ItemStack? {
        if (sertralineId != null) {
            return io.github.zzzyyylllty.sertraline.Sertraline.api()
                .buildItem(sertralineId, null, amount = amount)
        }
        val mat = material?.let { XMaterial.valueOf(it.uppercase()) }?.parseItem()
        return mat?.apply { this.amount = amount }
    }
}
