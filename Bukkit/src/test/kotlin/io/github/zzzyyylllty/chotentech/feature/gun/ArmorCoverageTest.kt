package io.github.zzzyyylllty.chotentech.feature.gun

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 护甲覆盖面回归测试。
 *
 * 背景：`ArmorManager.hitPart` 原先只返回 HEAD/BODY/STOMACH/LEGS，而韧性系统返回
 * HEAD/BODY/STOMACH/LARM/RARM/LLEG/RLEG 两套映射，导致手臂护甲永远匹配不上、
 * 腿甲也无法按单腿配置。这里锁定「别名展开」与「槽位默认覆盖」两条规则。
 */
class ArmorCoverageTest {

    @Test
    fun `legacy aliases expand to concrete limbs`() {
        assertEquals(setOf("LLEG", "RLEG"), ArmorManager.expandPart("LEGS"))
        assertEquals(setOf("LARM", "RARM"), ArmorManager.expandPart("ARMS"))
        assertEquals(setOf("LLEG"), ArmorManager.expandPart("lleg"))
        assertEquals(emptySet<String>(), ArmorManager.expandPart("  "))
    }

    @Test
    fun `leg armor covers both legs and stomach`() {
        assertTrue(ArmorManager.slotCovers("LEGS", "LLEG"))
        assertTrue(ArmorManager.slotCovers("LEGS", "RLEG"))
        // 旧名称仍然生效：配置里写 LEGS 的护甲依旧覆盖双腿。
        assertTrue(ArmorManager.slotCovers("LEGS", "LEGS"))
        assertTrue(ArmorManager.slotCovers("LEGS", "STOMACH"))
        assertFalse(ArmorManager.slotCovers("LEGS", "HEAD"))
        assertFalse(ArmorManager.slotCovers("LEGS", "LARM"))
    }

    @Test
    fun `boots only cover legs`() {
        assertTrue(ArmorManager.slotCovers("FEET", "LLEG"))
        assertTrue(ArmorManager.slotCovers("FEET", "RLEG"))
        assertFalse(ArmorManager.slotCovers("FEET", "STOMACH"))
        assertFalse(ArmorManager.slotCovers("FEET", "BODY"))
    }

    @Test
    fun `chest armor now covers arms after the mapping was unified`() {
        // 这条在旧实现里永远为 false：hitPart 从不返回 ARMS/LARM/RARM，
        // 而 BODY 槽默认集合里虽然写着 ARMS，实际也匹配不到具体肢。
        assertTrue(ArmorManager.slotCovers("BODY", "LARM"))
        assertTrue(ArmorManager.slotCovers("BODY", "RARM"))
        assertTrue(ArmorManager.slotCovers("BODY", "ARMS"))
        assertTrue(ArmorManager.slotCovers("BODY", "BODY"))
        assertTrue(ArmorManager.slotCovers("BODY", "STOMACH"))
        assertFalse(ArmorManager.slotCovers("BODY", "LLEG"))
    }

    @Test
    fun `helmet only covers head`() {
        assertTrue(ArmorManager.slotCovers("HEAD", "HEAD"))
        assertFalse(ArmorManager.slotCovers("HEAD", "BODY"))
        assertFalse(ArmorManager.slotCovers("HEAD", "LARM"))
    }
}