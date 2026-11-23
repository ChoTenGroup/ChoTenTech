package io.github.zzzyyylllty.chotentech.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `medkit.yml` 里 `cancel` / `slow` 的解析回归测试。
 *
 * 背景：`HealConfig` 早就定义了 `slow` 与 `cancelHeal` 两个字段，但 `loadMedkitConfig`
 * 从来没读过它们 —— 于是所有医疗包固定 -50% 移速、固定吃全套打断条件，
 * yml 里写 `slow:` / `cancel:` 一律无效。
 *
 * 这里只测**纯函数**部分：`Configuration.loadFromString` 依赖 taboolib 运行时里的
 * shaded night-config，在单测 classpath 上不可用。
 */
class MedkitConfigParseTest {

    @Test
    fun `cancel list is normalised to lower case and trimmed`() {
        assertEquals(setOf("sprint", "jump"), normalizeCancelConditions(listOf(" Sprint ", "JUMP")))
    }

    @Test
    fun `empty cancel list means no interruption at all`() {
        assertTrue(normalizeCancelConditions(emptyList()).isEmpty())
        assertTrue(normalizeCancelConditions(listOf("", "  ")).isEmpty())
    }

    @Test
    fun `unknown cancel entries are dropped`() {
        assertEquals(setOf("walk"), normalizeCancelConditions(listOf("walk", "walking", "flying")))
        // 只有这五个键真的被拦截逻辑读取
        assertEquals(setOf("walk", "sprint", "jump", "attack", "fall"),
            normalizeCancelConditions(KNOWN_CANCEL_CONDITIONS.toList()))
    }

    @Test
    fun `default cancel set is the full condition list`() {
        assertEquals(setOf("walk", "sprint", "jump", "attack", "fall"), DEFAULT_CANCEL_CONDITIONS)
    }

    @Test
    fun `slow defaults to fifty and is clamped to a percentage`() {
        // HealConfig.slow 的默认值与取样区间的语义（0~100 表示百分比减速）
        val default = HealConfig(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0).slow
        assertEquals(50.0, default, 1e-9)
        assertEquals(30.0, 30.0.coerceIn(0.0, 100.0), 1e-9)
        assertEquals(100.0, 250.0.coerceIn(0.0, 100.0), 1e-9)
        assertEquals(0.0, (-5.0).coerceIn(0.0, 100.0), 1e-9)
    }
}