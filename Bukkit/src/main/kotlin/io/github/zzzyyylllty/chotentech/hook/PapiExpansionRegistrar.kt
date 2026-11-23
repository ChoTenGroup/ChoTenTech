package io.github.zzzyyylllty.chotentech.hook

import io.github.zzzyyylllty.chotentech.util.DependencyHelper
import io.github.zzzyyylllty.chotentech.util.devLog
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake

/**
 * PlaceholderAPI 扩展的统一注册入口。
 *
 * 这里改成原生 `me.clip.placeholderapi.expansion.PlaceholderExpansion` + 手动注册，
 * 不再依赖 TabooLib 的 `taboolib.platform.compat.PlaceholderExpansion` 包装。
 *
 * 为什么不用包装：包装版是在 TabooLib 启动流程里自动注册的，注册时机与 PAPI 的
 * 加载顺序耦合，且 `/ctt reload` 不会重新注册 —— 重载后占位符会静默失效
 * （外部拿到的是原样的 `%chotentech_xxx%`）。手动注册把「检测 PAPI → 摘旧实例 →
 * 注册」收在自己手里，可以重复执行。
 *
 * 用**文件顶层函数**而不是 `object` 内的函数：本项目 `@Awake` 的惯例就是顶层函数
 * （见 `function/timer/autosave.kt`），由 TabooLib 扫描类文件时直接收集，
 * 不存在「object 没被引用因而不初始化」的问题。
 */

/** [KAngelAttributesExpansion] 是 class 不是 object，必须持有实例以便关服时摘除。 */
@Volatile
private var kAngel: KAngelAttributesExpansion? = null

@Awake(LifeCycle.ACTIVE)
fun registerPapiExpansions() {
    if (!DependencyHelper.papi) {
        devLog("[Papi] 未检测到 PlaceholderAPI，跳过占位符注册")
        return
    }

    val results = listOf(
        "chotentech" to runCatching { CTTPapiHook.registerSafely() },
        "quetiapine" to runCatching { QuetiapinePapiHook.registerSafely() },
        "kangelattributes" to runCatching {
            val expansion = kAngel ?: KAngelAttributesExpansion().also { kAngel = it }
            expansion.registerSafely()
        },
    )

    val ok = results.filter { it.second.getOrDefault(false) }.map { it.first }
    val failed = results.filterNot { it.second.getOrDefault(false) }.map { it.first }
    devLog(
        "[Papi] 占位符注册完成：成功=${ok.joinToString(",").ifEmpty { "无" }}" +
            if (failed.isEmpty()) "" else "，失败=${failed.joinToString(",")}",
    )
    results.forEach { (id, r) ->
        r.exceptionOrNull()?.let { devLog("[Papi] 注册 $id 时抛出异常: ${it.message}") }
    }
}

@Awake(LifeCycle.DISABLE)
fun unregisterPapiExpansions() {
    if (!DependencyHelper.papi) return
    runCatching { CTTPapiHook.unregisterSafely() }
    runCatching { QuetiapinePapiHook.unregisterSafely() }
    runCatching { kAngel?.unregisterSafely() }
    kAngel = null
}
