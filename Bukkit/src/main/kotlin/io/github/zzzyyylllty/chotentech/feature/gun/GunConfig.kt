package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog

/**
 * 枪械基础属性（gun.attributes 段，缺省值来自设计文档）。
 *
 * 这些值都是「基础值」：后续阶段在计算实际开火表现时，
 * 需要先与本段的加算项（[AddonData.attributes]）做加法，再考虑弹匣/子弹修正。
 */
data class GunAttributes(
    val machineEfficiency: Double = 100.0,
    val accuracy: Double = 75.0,
    val bulletSpeed: Double = 32.0,
    val fireRate: Double = 65.0,        // 发/分钟
    val barrelLength: Double = 1.2,
    val range: Double = 32.0,
    val recoilV: Double = 10.0,
    val recoilH: Double = 10.0,
    val durability: Double = 1000.0,
)

/**
 * 各类操作耗时（秒）。
 */
data class UsageTimes(
    val reload: Double = 2.0,
    val reloadFast: Double = 1.5,
    val removeMag: Double = 0.8,
    val bolt: Double = 0.9,
    val inspectChamber: Double = 1.48,
    val inspectMag: Double = 3.73,
    val insertAmmo: Double = 1.2,
    val removeAmmo: Double = 0.8,
    val fix: Double = 3.0,
    val fireMode: Double = 0.2,
    val draw: Double = 0.5,
    /**
     * 瞄准缩放倍数（`usage.zoom`，1.0 = 不放大）。
     *
     * 由 [io.github.zzzyyylllty.chotentech.feature.gun.GunAim.zoomOf] 读取，
     * 未配置时回落到 `GunSettings.scope.zoom`。
     */
    val zoom: Double = Double.NaN,
)

/**
 * 枪械静态配置（来自 Sertraline 物品的 `gun` 段）。
 *
 * @param id Sertraline 物品 ID（即 [io.github.zzzyyylllty.sertraline.Sertraline.api] 的 getId 结果）
 * @param caliber 口径，枪/弹匣/子弹之间靠它配对
 * @param auto 是否支持全自动（仅决定 [modes] 的缺省推导）
 * @param category 类别，缺省 pistol；[gunType] 与 [addonSlots] 的缺省推导都基于它
 * @param gunType 枪型，缺省为 `category.uppercase()`
 * @param recoilPattern 第 N 发 → 垂直后坐系数
 * @param shakePattern 第 N 发 → 水平偏移系数
 * @param modes SEMI / BURST / AUTO
 * @param addonSlots MUZZLE / SIGHT / GRIP / MAGWELL / STOCK
 */
data class GunData(
    val id: String,
    val caliber: String,
    val auto: Boolean = false,
    val category: String = "pistol",
    val gunType: String,
    val usage: UsageTimes = UsageTimes(),
    val attributes: GunAttributes = GunAttributes(),
    val recoilPattern: Map<Int, Double> = mapOf(1 to 1.0),   // 第 N 发 → 垂直后坐系数
    val shakePattern: Map<Int, Double> = mapOf(0 to 0.0),    // 第 N 发 → 水平偏移系数
    val burst: Int = 3,
    val modes: List<String> = emptyList(),                  // SEMI / BURST / AUTO
    val addonSlots: List<String> = emptyList(),             // MUZZLE/SIGHT/GRIP/MAGWELL/STOCK
    val heatCapacity: Double = 100.0,
    val heatPerShot: Double = 5.0,
    val heatCoolPerTick: Double = 0.06,
    val jamChance: Double = 1.5,
)

/**
 * 弹匣静态配置（来自 `mag` 段）。
 */
data class MagData(
    val id: String,
    val caliber: String,
    val capacity: Int = 8,
    val machineEfficiency: Double = 0.0,
    val loadingSpeed: Double = 0.0,
    val ammoInspectionAccuracy: Int = 0,
    val ammoInspectionTime: Double = 2.0,
    val allowFastReload: Boolean = true,
)

/**
 * 子弹静态配置（来自 `ammo` 段）。
 */
data class AmmoData(
    val id: String,
    val caliber: String,
    val penetration: Double = 50.0,
    val damage: Double = 25.0,
    val armorDamage: Double = 20.0,
    val accuracy: Double = 0.0,
    val recoil: Double = 100.0,   // 百分比
    val stuckChance: Double = 0.0,
    val critChance: Double = 0.0,
    val pellets: Int = 1,
    val speedMult: Double = 1.0,
)

/**
 * 配件静态配置（来自 `addon` 段；也兼容「`ammo` 段里塞 gunType + attributes」的旧写法）。
 *
 * @param gunType 适配枪型列表
 * @param slot MUZZLE / SIGHT / GRIP / MAGWELL / STOCK
 * @param attributes 与 `gun.attributes` 同名，叠加时做加法
 * @param suppressor 0=不消音，越小越安静（0~1）
 */
data class AddonData(
    val id: String,
    val gunType: List<String>,
    val slot: String,
    val caliber: String? = null,
    val attributes: Map<String, Double> = emptyMap(),  // 与 gun.attributes 同名，做加法
    val suppressor: Double = 0.0,
    val zoom: Double = 1.0,
    val thermal: Boolean = false,
)

/**
 * 护甲静态配置（来自 `armor` 段）。
 */
data class ArmorData(
    val id: String,
    val defense: Double = 0.0,
    val defenseLevel: Double = 0.0,
    val speed: Double = 0.0,
    val machineEfficiency: Double = 0.0,
    val material: String = "IRON",
    val part: List<String> = listOf("BODY"),
    val durability: Double = 500.0,
)

/**
 * Sertraline 配置段 → 强类型数据类的解析器。
 *
 * 所有解析都遵循两条原则：
 * 1. **不抛异常**：任一段解析失败一律返回 null 并打一条 devLog 告警，让调用方
 *    （[GunRegistry.reload]）可以「单条失败不影响整轮加载」。
 * 2. **数字类型宽容**：Sertraline 的 YAML 段来自 snakeyaml，同一个数字可能是
 *    Int / Long / Double / 甚至是字符串（引号包裹或占位符替换后），所以统一走
 *    `toString().toDoubleOrNull()` 兜底。
 */
object GunConfigParser {

    private val DEFAULT_USAGE = UsageTimes()
    private val DEFAULT_ATTRIBUTES = GunAttributes()

    // ==================== 各段解析入口 ====================

    /**
     * 解析 `gun` 段。`caliber` 为必要字段，缺失即失败。
     */
    fun parseGun(id: String, raw: Map<*, *>?): GunData? {
        return try {
            val map = unwrap(raw, "gun") ?: return fail("gun", id, "配置段为空")
            val caliber = str(map, "caliber", "").trim()
            if (caliber.isEmpty()) return fail("gun", id, "缺少必要字段 caliber")

            val category = str(map, "category", "pistol").trim().lowercase().ifEmpty { "pistol" }
            val auto = bool(map, "auto", false)
            val gunType = str(map, "gunType", "").trim().ifEmpty { category.uppercase() }

            val usage = sub(map, "usage")?.let { u ->
                UsageTimes(
                    reload = dbl(u, "reload", DEFAULT_USAGE.reload),
                    reloadFast = dbl(u, "reloadFast", DEFAULT_USAGE.reloadFast),
                    removeMag = dbl(u, "removeMag", DEFAULT_USAGE.removeMag),
                    bolt = dbl(u, "bolt", DEFAULT_USAGE.bolt),
                    inspectChamber = dbl(u, "inspectChamber", DEFAULT_USAGE.inspectChamber),
                    inspectMag = dbl(u, "inspectMag", DEFAULT_USAGE.inspectMag),
                    insertAmmo = dbl(u, "insertAmmo", DEFAULT_USAGE.insertAmmo),
                    removeAmmo = dbl(u, "removeAmmo", DEFAULT_USAGE.removeAmmo),
                    fix = dbl(u, "fix", DEFAULT_USAGE.fix),
                    fireMode = dbl(u, "fireMode", DEFAULT_USAGE.fireMode),
                    draw = dbl(u, "draw", DEFAULT_USAGE.draw),
                    // 未配置时保持 NaN，由 GunAim 回落到全局 scope.zoom
                    zoom = if (u.containsKey("zoom")) dbl(u, "zoom", DEFAULT_USAGE.zoom) else DEFAULT_USAGE.zoom,
                )
            } ?: DEFAULT_USAGE

            val attributes = sub(map, "attributes")?.let { a ->
                GunAttributes(
                    machineEfficiency = dbl(a, "machineEfficiency", DEFAULT_ATTRIBUTES.machineEfficiency),
                    accuracy = dbl(a, "accuracy", DEFAULT_ATTRIBUTES.accuracy),
                    bulletSpeed = dbl(a, "bulletSpeed", DEFAULT_ATTRIBUTES.bulletSpeed),
                    fireRate = dbl(a, "fireRate", DEFAULT_ATTRIBUTES.fireRate),
                    barrelLength = dbl(a, "barrelLength", DEFAULT_ATTRIBUTES.barrelLength),
                    range = dbl(a, "range", DEFAULT_ATTRIBUTES.range),
                    recoilV = dbl(a, "recoilV", DEFAULT_ATTRIBUTES.recoilV),
                    recoilH = dbl(a, "recoilH", DEFAULT_ATTRIBUTES.recoilH),
                    durability = dbl(a, "durability", DEFAULT_ATTRIBUTES.durability),
                )
            } ?: DEFAULT_ATTRIBUTES

            GunData(
                id = id,
                caliber = caliber,
                auto = auto,
                category = category,
                gunType = gunType,
                usage = usage,
                attributes = attributes,
                recoilPattern = numMap(sub(map, "recoil"), mapOf(1 to 1.0)),
                shakePattern = numMap(sub(map, "shake"), mapOf(0 to 0.0)),
                burst = int(map, "burst", 3).coerceAtLeast(1),
                modes = modesOf(map, auto),
                addonSlots = addonSlotsOf(map, category),
                heatCapacity = dbl(map, "heatCapacity", 100.0),
                heatPerShot = dbl(map, "heatPerShot", 5.0),
                heatCoolPerTick = dbl(map, "heatCoolPerTick", 0.06),
                jamChance = dbl(map, "jamChance", 1.5),
            )
        } catch (e: Exception) {
            devLog("[Gun] 解析 gun 段失败 (id=$id): ${e.message}")
            null
        }
    }

    /**
     * 解析 `mag` 段。`caliber` 为必要字段。
     */
    fun parseMag(id: String, raw: Map<*, *>?): MagData? {
        return try {
            val map = unwrap(raw, "mag") ?: return fail("mag", id, "配置段为空")
            val caliber = str(map, "caliber", "").trim()
            if (caliber.isEmpty()) return fail("mag", id, "缺少必要字段 caliber")

            MagData(
                id = id,
                caliber = caliber,
                capacity = int(map, "capacity", 8).coerceAtLeast(0),
                machineEfficiency = dbl(map, "machineEfficiency", 0.0),
                loadingSpeed = dbl(map, "loadingSpeed", 0.0),
                ammoInspectionAccuracy = int(map, "ammoInspectionAccuracy", 0),
                ammoInspectionTime = dbl(map, "ammoInspectionTime", 2.0),
                allowFastReload = bool(map, "allowFastReload", true),
            )
        } catch (e: Exception) {
            devLog("[Gun] 解析 mag 段失败 (id=$id): ${e.message}")
            null
        }
    }

    /**
     * 解析 `ammo` 段。`caliber` 为必要字段。
     */
    fun parseAmmo(id: String, raw: Map<*, *>?): AmmoData? {
        return try {
            val map = unwrap(raw, "ammo") ?: return fail("ammo", id, "配置段为空")
            val caliber = str(map, "caliber", "").trim()
            if (caliber.isEmpty()) return fail("ammo", id, "缺少必要字段 caliber")

            AmmoData(
                id = id,
                caliber = caliber,
                penetration = dbl(map, "penetration", 50.0),
                damage = dbl(map, "damage", 25.0),
                armorDamage = dbl(map, "armorDamage", 20.0),
                accuracy = dbl(map, "accuracy", 0.0),
                recoil = dbl(map, "recoil", 100.0),
                stuckChance = dbl(map, "stuckChance", 0.0),
                critChance = dbl(map, "critChance", 0.0),
                pellets = int(map, "pellets", 1).coerceAtLeast(1),
                speedMult = dbl(map, "speedMult", 1.0),
            )
        } catch (e: Exception) {
            devLog("[Gun] 解析 ammo 段失败 (id=$id): ${e.message}")
            null
        }
    }

    /**
     * 解析 `addon` 段（也用于兼容写法：`ammo` 段里带 gunType + attributes 的情形）。
     *
     * [AddonData.slot] 是结构性必要字段（缺了就没法判断装到哪个槽位），缺失即失败；
     * [AddonData.gunType] 缺失时退化为空列表（表示「不限定枪型」），不视为失败。
     */
    fun parseAddon(id: String, raw: Map<*, *>?): AddonData? {
        return try {
            val map = unwrap(raw, "addon") ?: return fail("addon", id, "配置段为空")
            val slot = str(map, "slot", "").trim()
            if (slot.isEmpty()) return fail("addon", id, "缺少必要字段 slot")

            AddonData(
                id = id,
                gunType = strList(map, "gunType") ?: emptyList(),
                slot = slot.uppercase(),
                caliber = str(map, "caliber", "").trim().ifEmpty { null },
                attributes = dblMap(sub(map, "attributes")),
                suppressor = dbl(map, "suppressor", 0.0),
                zoom = dbl(map, "zoom", 1.0),
                thermal = bool(map, "thermal", false),
            )
        } catch (e: Exception) {
            devLog("[Gun] 解析 addon 段失败 (id=$id): ${e.message}")
            null
        }
    }

    /**
     * 解析 `armor` 段。全字段都有缺省值，只要段存在就能解析出对象。
     */
    fun parseArmor(id: String, raw: Map<*, *>?): ArmorData? {
        return try {
            val map = unwrap(raw, "armor") ?: return fail("armor", id, "配置段为空")
            val parts = strList(map, "part")
                ?.map { it.trim().uppercase() }
                ?.filter { it.isNotEmpty() }
                ?.ifEmpty { null }
                ?: listOf("BODY")

            ArmorData(
                id = id,
                defense = dbl(map, "defense", 0.0),
                defenseLevel = dbl(map, "defenseLevel", 0.0),
                speed = dbl(map, "speed", 0.0),
                machineEfficiency = dbl(map, "machineEfficiency", 0.0),
                material = str(map, "material", "IRON").trim().ifEmpty { "IRON" },
                part = parts,
                durability = dbl(map, "durability", 500.0),
            )
        } catch (e: Exception) {
            devLog("[Gun] 解析 armor 段失败 (id=$id): ${e.message}")
            null
        }
    }

    // ==================== 段兼容工具 ====================

    /**
     * 兼容「传入整段 ModernSItem.config」的调用方式：
     * 若 raw 里还有一层同名子段（如 config["gun"]），且 raw 自身不像该段，则下钻一层。
     */
    private fun unwrap(raw: Map<*, *>?, key: String): Map<*, *>? {
        if (raw == null) return null
        val nested = sub(raw, key)
        // 只有当 raw 自身「不像」该段（顶层没有 caliber）时才下钻，
        // 避免把已经是段内容、只是恰好也叫 gun/ammo 的配置二次包装。
        return if (nested != null && !raw.containsKey("caliber")) nested else raw
    }

    /**
     * `ammo` 段是否其实是 addon（设计里要求的兼容写法）：
     * 同时含 gunType 与 attributes，且**不含** damage / penetration。
     */
    fun isAddonShapedAmmo(raw: Map<*, *>?): Boolean {
        if (raw == null) return false
        val map = sub(raw, "ammo") ?: raw
        return map.containsKey("gunType") &&
            map.containsKey("attributes") &&
            !map.containsKey("damage") &&
            !map.containsKey("penetration")
    }

    // ==================== 缺省推导 ====================

    /** modes 缺省推导：auto=false → [SEMI]；auto=true → [SEMI, AUTO] */
    private fun modesOf(map: Map<*, *>, auto: Boolean): List<String> {
        val configured = strList(map, "modes")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
        if (!configured.isNullOrEmpty()) return configured
        return if (auto) listOf("SEMI", "AUTO") else listOf("SEMI")
    }

    /** addonSlots 缺省推导：pistol → 4 槽；其它 → 3 槽 */
    private fun addonSlotsOf(map: Map<*, *>, category: String): List<String> {
        val configured = strList(map, "addonSlots")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
        if (!configured.isNullOrEmpty()) return configured
        return if (category == "pistol") {
            listOf("MUZZLE", "SIGHT", "GRIP", "MAGWELL")
        } else {
            listOf("MUZZLE", "SIGHT", "GRIP")
        }
    }

    // ==================== Map 读取小工具 ====================

    private fun <T> fail(section: String, id: String, reason: String): T? {
        devLog("[Gun] 解析 $section 段失败 (id=$id): $reason")
        return null
    }

    private fun str(map: Map<*, *>, key: String, def: String): String {
        val value = map[key] ?: return def
        val text = value.toString()
        return if (text.isEmpty()) def else text
    }

    private fun dbl(map: Map<*, *>, key: String, def: Double): Double =
        map[key]?.toString()?.trim()?.toDoubleOrNull() ?: def

    private fun int(map: Map<*, *>, key: String, def: Int): Int =
        map[key]?.toString()?.trim()?.toDoubleOrNull()?.toInt() ?: def

    private fun bool(map: Map<*, *>, key: String, def: Boolean): Boolean =
        when (map[key]?.toString()?.trim()?.lowercase()) {
            null, "" -> def
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> def
        }

    private fun sub(map: Map<*, *>, key: String): Map<*, *>? = map[key] as? Map<*, *>

    /**
     * 读取字符串列表，同时容忍单个字符串（YAML 里常写成 `part: BODY` 而不是列表）。
     */
    private fun strList(map: Map<*, *>, key: String): List<String>? {
        val value = map[key] ?: return null
        val items = when (value) {
            is Collection<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> listOf(value)
        }
        val parsed = items.mapNotNull { it?.toString()?.trim()?.takeIf { text -> text.isNotEmpty() } }
        return if (parsed.isEmpty()) null else parsed
    }

    /**
     * 把「键是数字」的 YAML map（如 `1: 1`）转成按 key 升序的 LinkedHashMap<Int, Double>。
     */
    private fun numMap(raw: Map<*, *>?, def: Map<Int, Double>): Map<Int, Double> {
        if (raw == null || raw.isEmpty()) return def
        val parsed = LinkedHashMap<Int, Double>()
        for ((k, v) in raw) {
            val index = k?.toString()?.trim()?.toDoubleOrNull()?.toInt() ?: continue
            val value = v?.toString()?.trim()?.toDoubleOrNull() ?: continue
            parsed[index] = value
        }
        if (parsed.isEmpty()) return def
        val sorted = LinkedHashMap<Int, Double>(parsed.size)
        for (index in parsed.keys.sorted()) {
            sorted[index] = parsed.getValue(index)
        }
        return sorted
    }

    /** addon.attributes：键是属性名，值是数字，统一转 Double，非数字项丢弃。 */
    private fun dblMap(raw: Map<*, *>?): Map<String, Double> {
        if (raw == null || raw.isEmpty()) return emptyMap()
        val parsed = LinkedHashMap<String, Double>(raw.size)
        for ((k, v) in raw) {
            val key = k?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val value = v?.toString()?.trim()?.toDoubleOrNull() ?: continue
            parsed[key] = value
        }
        return parsed
    }
}
