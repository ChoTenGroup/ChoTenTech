package io.github.zzzyyylllty.chotentech.shop

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import java.io.File

object GiftPackManager {

    private val giftPacks = linkedMapOf<String, GiftPackConfig>()

    fun loadAll() {
        giftPacks.clear()
        val dir = File(getDataFolder(), "giftpacks")
        if (!dir.exists()) {
            dir.mkdirs()
            info("§e[GiftPack] Gift packs directory created, place your gift pack YAML files in giftpacks/")
            return
        }

        dir.listFiles { f -> f.extension == "yml" }?.forEach { file ->
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                for (key in config.getKeys(false)) {
                    val sec = config.getConfigurationSection(key) ?: continue
                    val pack = parseGiftPack(key, sec)
                    giftPacks[key] = pack
                    info("§a[GiftPack] Loaded gift pack: ${pack.name} ($key)")
                }
            } catch (e: Exception) {
                info("§c[GiftPack] Failed to load ${file.name}: ${e.message}")
            }
        }
        info("§a[GiftPack] Loaded ${giftPacks.size} gift pack(s) total")
    }

    fun getPack(key: String): GiftPackConfig? = giftPacks[key]

    private fun parseGiftPack(key: String, sec: ConfigurationSection): GiftPackConfig {
        val name = sec.getString("name") ?: key
        val price = sec.getLong("price", 0)
        val currency = sec.getString("currency") ?: "point"
        val categories = parseCategories(sec.getConfigurationSection("categories") ?: YamlConfiguration())
        val priceModifiers = parsePriceModifiers(sec.getConfigurationSection("price_modifiers"))
        return GiftPackConfig(key, name, price, currency, categories, priceModifiers)
    }

    private fun parseCategories(sec: ConfigurationSection): LinkedHashMap<String, GiftPackCategory> {
        val categories = linkedMapOf<String, GiftPackCategory>()
        for (catKey in sec.getKeys(false)) {
            val catSec = sec.getConfigurationSection(catKey) ?: continue
            val displayName = catSec.getString("display_name") ?: catKey
            val maxSelect = catSec.getInt("max_select", 1)
            val items = parseCategoryItems(catSec.getConfigurationSection("items") ?: continue)
            categories[catKey] = GiftPackCategory(displayName, items, maxSelect)
        }
        return categories
    }

    private fun parseCategoryItems(sec: ConfigurationSection): List<GiftPackSelectableItem> {
        val items = mutableListOf<GiftPackSelectableItem>()
        for (itemKey in sec.getKeys(false)) {
            val itemSec = sec.getConfigurationSection(itemKey) ?: continue
            val id = itemSec.getString("id") ?: continue
            val displayName = itemSec.getString("display_name") ?: id
            items.add(GiftPackSelectableItem(id, displayName))
        }
        return items
    }

    private fun parsePriceModifiers(sec: ConfigurationSection?): Map<String, Long> {
        if (sec == null) return emptyMap()
        val modifiers = mutableMapOf<String, Long>()
        for (key in sec.getKeys(false)) {
            modifiers[key] = sec.getLong(key)
        }
        return modifiers
    }
}
