package io.github.zzzyyylllty.chotentech.feature.gun

import io.github.zzzyyylllty.chotentech.feature.gun.item.GunItemProcessors
import io.github.zzzyyylllty.chotentech.util.devLog
import io.github.zzzyyylllty.sertraline.Sertraline
import io.github.zzzyyylllty.sertraline.event.SertralineReloadEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFolder
import java.io.File

/**
 * Loads the bundled gun item examples from ChoTenTech's own data folder through Sertraline's
 * public single-file parser API. These definitions are therefore processed by the same native
 * item segments and plugin-registered ItemLoadEvent processors as normal workspace files.
 */
object GunExampleItems {

    private val directory: File
        get() = File(getDataFolder(), "gun-examples")

    @SubscribeEvent
    fun onSertralineReload(event: SertralineReloadEvent) {
        // Sertraline's full workspace reload atomically replaces itemMap **and** drops every
        // processor registered through itemManager(), so both have to be restored here:
        //  1) re-register our item segments (itemdef / model / mag / ammo / addon / armor)
        //  2) re-add the ChoTenTech-owned item definitions themselves
        GunItemProcessors.register()
        load()
        GunRegistry.reload()
    }

    /**
     * Extract bundled examples on first use and parse every YAML file. Repeated calls replace
     * definitions by ID, so `/ctt reload` can refresh edits without duplicate registry entries.
     *
     * @return number of successfully parsed files
     */
    fun load(): Int {
        val dir = directory
        if (!dir.exists()) {
            try {
                releaseResourceFolder("gun-examples")
            } catch (e: Exception) {
                devLog("[Gun] 无法释放内置示例文件: ${e.message}")
            }
        }
        if (!dir.isDirectory) return 0

        val files = dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("yml", "yaml") }
            .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
            .toList()
        if (files.isEmpty()) return 0

        val api = try {
            Sertraline.api()
        } catch (e: Exception) {
            devLog("[Gun] Sertraline API 暂不可用，跳过示例物品加载: ${e.message}")
            return 0
        }

        var loaded = 0
        for (file in files) {
            try {
                if (api.loadItemsFromFile(file)) {
                    loaded++
                } else {
                    devLog("[Gun] 示例物品文件加载失败: ${file.relativeTo(dir).invariantSeparatorsPath}")
                }
            } catch (e: Exception) {
                devLog("[Gun] 示例物品文件加载异常 ${file.name}: ${e.message}")
            }
        }
        devLog("[Gun] ChoTenTech 内置示例物品文件加载: $loaded/${files.size}")
        return loaded
    }
}
