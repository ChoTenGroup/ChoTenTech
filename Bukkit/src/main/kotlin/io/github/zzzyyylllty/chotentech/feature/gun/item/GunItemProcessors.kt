package io.github.zzzyyylllty.chotentech.feature.gun.item

import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.Sertraline

/**
 * 向 Sertraline 注册枪械系统用到的物品段落处理器。
 *
 * ### 关键约束
 * 必须走 [io.github.zzzyyylllty.sertraline.api.SertralineAPI.registerItemProcessor]
 * （而不是 `itemManager().registerProcessor(...)`）：
 * Sertraline 每次重载都会 `unregisterAllProcessor()` 再只挂回内置处理器，
 * 直接注册的处理器会在第一次 `/sreload` 之后全部消失 —— 表现为
 * **枪械 / 弹匣 / 子弹 / 配件全部没有名称与描述、没有模型，弹匣也不再写入运行时字段**。
 *
 * ### 幂等
 * 重复调用是安全的：同名段落直接覆盖。任何重载路径都可以重新调用一次。
 */
object GunItemProcessors {

    /** 段落名 → 适配器，集中在这里，避免注册与实现两处漂移。 */
    private fun processors(): Map<String, (org.bukkit.inventory.ItemStack, io.github.zzzyyylllty.sertraline.data.AbstractSItem, org.bukkit.entity.Player?) -> org.bukkit.inventory.ItemStack> =
        linkedMapOf(
            "itemdef" to ::itemdefAdapter,
            "model" to ::modelAdapter,
            "mag" to ::magAdapter,
            "ammo" to ::ammoAdapter,
            "addon" to ::addonAdapter,
            "armor" to ::armorAdapter,
        )

    /** 我们负责的段落名（排障命令用）。 */
    fun segmentNames(): List<String> = processors().keys.toList()

    /**
     * 注册全部段落处理器（幂等）。
     *
     * @return true = 已注册；false = Sertraline 尚未就绪（调用方可在重载路径再试一次）
     */
    fun register(): Boolean {
        return try {
            val api = Sertraline.api()
            for ((name, adapter) in processors()) {
                api.registerItemProcessor(name) { sItem, item, player -> adapter(item, sItem, player) }
            }
            devLog("[Gun] 已注册物品段落处理器: ${api.registeredItemProcessors()}")
            true
        } catch (e: Throwable) {
            // Sertraline 未就绪 / API 版本过旧：不能设为「已完成」，否则后面永远不再尝试。
            devLog("[Gun] 注册物品段落处理器失败（将在下次重载重试）: ${e::class.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * 构建后核对：名称 / 描述缺失时返回原因串，全部正常返回 null。
     *
     * 只在给了物品之后跑一次，成本可以忽略；判据是「配置里写了 name/desc，但物品上没有」，
     * 所以不会误报「本来就没配名称」的物品。
     */
    fun probeBuilt(item: org.bukkit.inventory.ItemStack): String? {
        val id = try {
            Sertraline.api().getId(item)
        } catch (_: Throwable) {
            null
        } ?: return null
        val sItem = try {
            Sertraline.api().getItem(id)
        } catch (_: Throwable) {
            null
        } ?: return null

        val itemdef = sItem.config["itemdef"] as? Map<*, *> ?: return null
        val wantsName = itemdef["name"]?.toString()?.isNotBlank() == true
        val wantsDesc = itemdef["desc"] != null

        val meta = try {
            item.itemMeta
        } catch (_: Throwable) {
            return "读取物品 meta 失败"
        } ?: return "读取物品 meta 失败"

        val hasName = try {
            meta.hasDisplayName()
        } catch (_: Throwable) {
            false
        }
        val loreSize = try {
            meta.lore()?.size ?: 0
        } catch (_: Throwable) {
            0
        }

        val problems = mutableListOf<String>()
        if (wantsName && !hasName) problems += "缺少名称"
        if (wantsDesc && loreSize <= 0) problems += "缺少描述"
        return if (problems.isEmpty()) null else "${problems.joinToString("、")}（itemdef 段落未执行）"
    }

    /**
     * 现场构建一个物品，报告名称 / 描述 / 模型是否真的被写上去（`/ctt gun processors` 用）。
     *
     * 这是判断「处理器到底跑没跑」最直接的办法：不依赖日志、不依赖 NBT 猜测。
     */
    fun probe(id: String): String {
        val item = try {
            Sertraline.api().buildItem(id, null, amount = 1)
        } catch (e: Throwable) {
            return "<#E08A98>构建失败: ${e::class.simpleName}: ${e.message}"
        } ?: return "<#E08A98>构建失败: 物品 ID 不存在（$id）"

        val meta = try {
            item.itemMeta
        } catch (_: Throwable) {
            null
        } ?: return "<#E08A98>构建出的物品没有 meta"

        val name = try {
            meta.displayName()?.let { io.github.zzzyyylllty.chotentech.util.minimessage.mmPlainUtil.serialize(it) }
        } catch (_: Throwable) {
            null
        }
        val lore = try {
            meta.lore()?.size ?: 0
        } catch (_: Throwable) {
            0
        }
        val model = try {
            meta.hasCustomModelData() || meta.itemModel != null
        } catch (_: Throwable) {
            false
        }

        val nameText = if (name.isNullOrEmpty()) "<#E08A98>无" else "<#8FD4A6>$name"
        val loreText = if (lore <= 0) "<#E08A98>无" else "<#8FD4A6>${lore} 行"
        val modelText = if (model) "<#8FD4A6>有" else "<#EFDF8F>无"
        return "<#A9B4C3>名称 $nameText <#6E7A8C>|</#6E7A8C> <#A9B4C3>描述 $loreText" +
            " <#6E7A8C>|</#6E7A8C> <#A9B4C3>模型 $modelText"
    }
}
