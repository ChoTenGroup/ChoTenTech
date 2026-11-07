package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.Sertraline
import org.bukkit.inventory.ItemStack

/**
 * 枪械系统 P0 注册表：把 Sertraline 物品里静态的 `gun` / `mag` / `ammo` / `addon` / `armor`
 * 段解析成强类型数据类，并只读缓存起来供后续阶段（射击、换弹、护甲结算）查询。
 *
 * 设计要点：
 * - **只读 + 原子重建**：[reload] 先解析到局部表，再一次性发布不可变快照。
 * - **单条失败隔离**：每一条配置单独 try/catch，一条坏 YAML 不能中断整轮加载。
 * - **线程安全**：`@Volatile` 快照可被任意线程读取，异步 reload 不会产生空表窗口。
 * - **幂等**：多次 reload 结果一致，不做任何「只 put 不 clear」的累积。
 */
object GunRegistry {

    private data class RegistrySnapshot(
        val guns: Map<String, GunData> = emptyMap(),
        val mags: Map<String, MagData> = emptyMap(),
        val ammos: Map<String, AmmoData> = emptyMap(),
        val addons: Map<String, AddonData> = emptyMap(),
        val armors: Map<String, ArmorData> = emptyMap(),
    )

    /** 异步解析期间主线程继续读取旧快照，发布后五张表在同一代快照中。 */
    @Volatile
    private var snapshot = RegistrySnapshot()

    // ==================== 加载 ====================

    /**
     * 全量原子重建注册表；可在异步重载中调用，发布前读线程始终看到旧快照。
     */
    fun reload() {
        val items = try {
            Sertraline.api().getAllItems()
        } catch (e: Exception) {
            devLog("[Gun] 读取 Sertraline 物品表失败，保留上一份枪械注册表: ${e.message}")
            return
        }

        // 全部解析到局部表；完成前绝不修改正在服务主线程的旧快照。
        val guns = mutableMapOf<String, GunData>()
        val mags = mutableMapOf<String, MagData>()
        val ammos = mutableMapOf<String, AmmoData>()
        val addons = mutableMapOf<String, AddonData>()
        val armors = mutableMapOf<String, ArmorData>()
        if (items.isEmpty()) {
            devLog("[Gun] Sertraline 未返回任何物品，枪械注册表为空")
        }

        var failed = 0
        for ((key, item) in items) {
            try {
                // 以 getAllItems() 的 key 为准（这正是 getId() / buildItem() 使用的 ID），
                // item.key 只在极端情况下做兜底。
                val id = key.ifBlank { item.key }
                loadOne(id, item.config, guns, mags, ammos, addons, armors)
            } catch (e: Exception) {
                failed++
                devLog("[Gun] 加载物品 ${key} 失败，已跳过: ${e.message}")
            }
        }

        val loaded = RegistrySnapshot(
            guns = guns.toMap(),
            mags = mags.toMap(),
            ammos = ammos.toMap(),
            addons = addons.toMap(),
            armors = armors.toMap(),
        )
        snapshot = loaded
        val total = loaded.guns.size + loaded.mags.size + loaded.ammos.size + loaded.addons.size + loaded.armors.size
        devLog(
            "[Gun] 枪械注册表已加载: gun=${loaded.guns.size} mag=${loaded.mags.size} ammo=${loaded.ammos.size} " +
                "addon=${loaded.addons.size} armor=${loaded.armors.size} (总条目 $total, 失败 $failed)"
        )
    }

    /**
     * 解析单个物品的全部枪械段。同一个物品理论上可以同时带多段（例如枪 + 默认弹匣），
     * 所以这里逐段独立处理，互不影响。
     */
    private fun loadOne(
        id: String,
        config: Map<*, *>,
        guns: MutableMap<String, GunData>,
        mags: MutableMap<String, MagData>,
        ammos: MutableMap<String, AmmoData>,
        addons: MutableMap<String, AddonData>,
        armors: MutableMap<String, ArmorData>,
    ) {
        section(config, "gun")?.let { raw ->
            guard("gun", id) { GunConfigParser.parseGun(id, raw)?.also { guns[id] = it } }
        }
        section(config, "mag")?.let { raw ->
            guard("mag", id) { GunConfigParser.parseMag(id, raw)?.also { mags[id] = it } }
        }
        section(config, "ammo")?.let { raw ->
            // 兼容写法：ammo 段同时带 gunType + attributes 且没有 damage/penetration 时按配件解析
            if (GunConfigParser.isAddonShapedAmmo(raw)) {
                guard("ammo(addon 兼容)", id) { GunConfigParser.parseAddon(id, raw)?.also { addons[id] = it } }
            } else {
                guard("ammo", id) { GunConfigParser.parseAmmo(id, raw)?.also { ammos[id] = it } }
            }
        }
        section(config, "addon")?.let { raw ->
            guard("addon", id) { GunConfigParser.parseAddon(id, raw)?.also { addons[id] = it } }
        }
        section(config, "armor")?.let { raw ->
            guard("armor", id) { GunConfigParser.parseArmor(id, raw)?.also { armors[id] = it } }
        }
    }

    private fun section(config: Map<*, *>, key: String): Map<*, *>? = config[key] as? Map<*, *>

    private fun guard(section: String, id: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            devLog("[Gun] 注册 $section 段失败 (id=$id)，已跳过: ${e.message}")
        }
    }

    // ==================== 按 ID 查询 ====================

    fun gun(id: String?): GunData? = if (id == null) null else snapshot.guns[id]

    fun mag(id: String?): MagData? = if (id == null) null else snapshot.mags[id]

    fun ammo(id: String?): AmmoData? = if (id == null) null else snapshot.ammos[id]

    fun armor(id: String?): ArmorData? = if (id == null) null else snapshot.armors[id]

    fun addon(id: String?): AddonData? = if (id == null) null else snapshot.addons[id]

    // ==================== 按物品查询 ====================

    fun gunOf(item: ItemStack?): GunData? = gun(idOf(item))

    fun magOf(item: ItemStack?): MagData? = mag(idOf(item))

    fun ammoOf(item: ItemStack?): AmmoData? = ammo(idOf(item))

    fun armorOf(item: ItemStack?): ArmorData? = armor(idOf(item))

    fun addonOf(item: ItemStack?): AddonData? = addon(idOf(item))

    /**
     * 物品 → Sertraline ID。null / 空气 / 数量 0 / 非 Sertraline 物品一律返回 null，
     * 调用方不需要自己判空。
     */
    private fun idOf(item: ItemStack?): String? {
        if (item == null) return null
        return try {
            if (item.type.isAir || item.amount <= 0) null else Sertraline.api().getId(item)
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 聚合查询 ====================

    /** 按枪型筛选（忽略大小写）。 */
    fun byGunType(type: String): List<GunData> =
        snapshot.guns.values.filter { it.gunType.equals(type, ignoreCase = true) }

    /** 按口径筛选子弹（忽略大小写）。 */
    fun byCaliber(caliber: String): List<AmmoData> =
        snapshot.ammos.values.filter { it.caliber.equals(caliber, ignoreCase = true) }

    /** 所有枪械的快照。 */
    fun allGuns(): Collection<GunData> = ArrayList(snapshot.guns.values)

    /** 所有弹匣的快照。 */
    fun allMags(): Collection<MagData> = ArrayList(snapshot.mags.values)

    /** 所有子弹的快照。 */
    fun allAmmos(): Collection<AmmoData> = ArrayList(snapshot.ammos.values)

    /** 所有配件的快照。 */
    fun allAddons(): Collection<AddonData> = ArrayList(snapshot.addons.values)

    /** 所有护甲的快照。 */
    fun allArmors(): Collection<ArmorData> = ArrayList(snapshot.armors.values)

    /** 注册表里出现过的全部 Sertraline ID（调试命令补全用）。 */
    fun allIds(): List<String> {
        val current = snapshot
        return (current.guns.keys + current.mags.keys + current.ammos.keys + current.addons.keys + current.armors.keys)
            .distinct().sorted()
    }

    // ==================== 统计 ====================

    fun gunCount(): Int = snapshot.guns.size

    fun magCount(): Int = snapshot.mags.size

    fun ammoCount(): Int = snapshot.ammos.size

    fun addonCount(): Int = snapshot.addons.size

    fun armorCount(): Int = snapshot.armors.size

    /** 全部段条目数之和（调试命令 /ctt gun info 的「总数」）。 */
    fun size(): Int = gunCount() + magCount() + ammoCount() + addonCount() + armorCount()
}
