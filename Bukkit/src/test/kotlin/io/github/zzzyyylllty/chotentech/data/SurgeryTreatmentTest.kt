package io.github.zzzyyylllty.chotentech.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurgeryTreatmentTest {

    private fun body(): PlayerBodyHealth = PlayerBodyHealth()

    private fun effect(type: String) = PlayerStatusEffect(type, 1, Long.MAX_VALUE)

    @Test
    fun `surgery restores a destroyed part that normal treatment refuses`() {
        val body = body()
        val head = body.parts.getValue("HEAD")
        head.toughness = 0.0
        assertTrue(head.destroyed)
        // 常规治疗走 heal()，对损毁部位无效——这正是手术存在的理由。
        head.heal(10.0)
        assertEquals(0.0, head.toughness, 1e-9)

        val result = SurgeryTreatment.apply(body, "HEAD", 100.0, 1.0,
            ToughnessRestore.Flat(10.0), emptyMap())
        assertEquals(10.0, result.restored, 1e-9)
        assertEquals(10.0, head.toughness, 1e-9)
        assertFalse(head.destroyed)
        assertEquals("DAMAGED", head.state)
    }

    @Test
    fun `percent toughness scales with the part maximum`() {
        val body = body()
        val arm = body.parts.getValue("LARM")   // 最大 50
        val head = body.parts.getValue("HEAD")  // 最大 45
        arm.toughness = 0.0
        head.toughness = 0.0

        val armResult = SurgeryTreatment.apply(body, "LARM", 100.0, 1.0,
            ToughnessRestore.Percent(10.0), emptyMap())
        val headResult = SurgeryTreatment.apply(body, "HEAD", 100.0, 1.0,
            ToughnessRestore.Percent(10.0), emptyMap())
        assertEquals(5.0, armResult.restored, 1e-9)
        assertEquals(4.5, headResult.restored, 1e-9)
    }

    @Test
    fun `flat and percent parse from the same yml value`() {
        assertEquals(ToughnessRestore.Flat(10.0), ToughnessRestore.parse(10))
        assertEquals(ToughnessRestore.Flat(10.0), ToughnessRestore.parse(10.0))
        assertEquals(ToughnessRestore.Flat(10.0), ToughnessRestore.parse("10"))
        assertEquals(ToughnessRestore.Percent(10.0), ToughnessRestore.parse("10%"))
        assertEquals(ToughnessRestore.Percent(12.5), ToughnessRestore.parse("12.5 %"))
        assertEquals(5.0, ToughnessRestore.parse("10%").amountFor(50.0), 1e-9)
        assertEquals(10.0, ToughnessRestore.parse(10).amountFor(50.0), 1e-9)
    }

    @Test
    fun `restore never exceeds the part maximum`() {
        val body = body()
        val arm = body.parts.getValue("LARM")
        arm.toughness = 46.0
        val result = SurgeryTreatment.apply(body, "LARM", 100.0, 1.0,
            ToughnessRestore.Flat(10.0), emptyMap())
        assertEquals(4.0, result.restored, 1e-9)
        assertEquals(50.0, arm.toughness, 1e-9)
        // 打满即结束，不需要再拖下一轮。
        assertEquals(SurgeryTreatment.Stop.PART_FULL, result.stop)
    }

    @Test
    fun `durability cost is charged once per productive round only`() {
        val body = body()
        val arm = body.parts.getValue("LARM")
        arm.toughness = 50.0 // 已满，没有可执行内容
        val idle = SurgeryTreatment.apply(body, "LARM", 100.0, 1.0,
            ToughnessRestore.Flat(10.0), emptyMap())
        assertFalse(idle.worked)
        assertEquals(100.0, idle.durability, 1e-9)
        assertEquals(SurgeryTreatment.Stop.NOTHING, idle.stop)

        arm.toughness = 30.0
        val busy = SurgeryTreatment.apply(body, "LARM", 100.0, 1.0,
            ToughnessRestore.Flat(10.0), emptyMap())
        assertTrue(busy.worked)
        assertEquals(99.0, busy.durability, 1e-9)
        assertEquals(SurgeryTreatment.Stop.NONE, busy.stop)
    }

    @Test
    fun `debuff removal is charged on top of the round cost`() {
        val body = body()
        val leg = body.parts.getValue("RLEG")
        leg.toughness = 20.0
        leg.effects[FRACTURE] = effect(FRACTURE)

        val result = SurgeryTreatment.apply(body, "RLEG", 100.0, 1.0,
            ToughnessRestore.Flat(10.0), mapOf(FRACTURE to 20.0))
        assertEquals(listOf(FRACTURE), result.removed)
        assertFalse(leg.effects.containsKey(FRACTURE))
        // 20（移除）+ 1（本轮）= 21
        assertEquals(79.0, result.durability, 1e-9)
        assertEquals(30.0, leg.toughness, 1e-9)
    }

    @Test
    fun `unaffordable debuff aborts instead of skipping to toughness`() {
        val body = body()
        val leg = body.parts.getValue("RLEG")
        leg.toughness = 0.0
        leg.effects[MAJOR_BLEED] = effect(MAJOR_BLEED)

        val result = SurgeryTreatment.apply(body, "RLEG", 5.0, 1.0,
            ToughnessRestore.Flat(10.0), mapOf(MAJOR_BLEED to 180.0))
        assertEquals(SurgeryTreatment.Stop.UNAFFORDABLE_DEBUFF, result.stop)
        // 不绕过：韧性与耐久都没动。
        assertEquals(0.0, result.restored, 1e-9)
        assertEquals(5.0, result.durability, 1e-9)
        assertEquals(0.0, leg.toughness, 1e-9)
        assertTrue(leg.effects.containsKey(MAJOR_BLEED))
    }

    @Test
    fun `removing major bleed leaves a wound`() {
        val body = body()
        val arm = body.parts.getValue("LARM")
        arm.toughness = 10.0
        arm.effects[MAJOR_BLEED] = effect(MAJOR_BLEED)
        SurgeryTreatment.apply(body, "LARM", 500.0, 1.0,
            ToughnessRestore.Flat(10.0), mapOf(MAJOR_BLEED to 180.0))
        assertFalse(arm.effects.containsKey(MAJOR_BLEED))
        assertTrue(arm.effects.containsKey(WOUND))
    }

    @Test
    fun `running out of durability stops the operation`() {
        val body = body()
        val head = body.parts.getValue("HEAD")
        head.toughness = 0.0
        // 耐久 1，本轮消耗 1：做完这一轮就耗尽。
        val result = SurgeryTreatment.apply(body, "HEAD", 1.0, 1.0,
            ToughnessRestore.Flat(10.0), emptyMap())
        assertEquals(10.0, result.restored, 1e-9)
        assertEquals(0.0, result.durability, 1e-9)
        assertEquals(SurgeryTreatment.Stop.NO_DURABILITY, result.stop)
    }

    @Test
    fun `a destroyed part needs several rounds to come back`() {
        val body = body()
        val leg = body.parts.getValue("LLEG") // 最大 50
        leg.toughness = 0.0
        var durability = 100.0
        var rounds = 0
        while (true) {
            val result = SurgeryTreatment.apply(body, "LLEG", durability, 1.0,
                ToughnessRestore.Flat(10.0), emptyMap())
            durability = result.durability
            rounds++
            if (result.finished) break
            if (rounds > 20) break
        }
        assertEquals(5, rounds)
        assertEquals(50.0, leg.toughness, 1e-9)
        assertEquals(95.0, durability, 1e-9)
        assertFalse(leg.destroyed)
    }

    @Test
    fun `an empty toughness config makes the surgery inoperable`() {
        val config = SurgeryConfig(
            durabilityCost = 1.0, costFood = 10.0, costSan = 0.0, costWater = 10.0,
            preTime = 3.0, perTime = 10.0, postTime = 1.5,
            cancellable = false, moveable = false, jumpable = false,
            toughness = ToughnessRestore.Flat(0.0), removeDebuff = emptyMap()
        )
        assertFalse(config.operable)

        val withDebuff = config.copy(removeDebuff = mapOf(FRACTURE to 50.0))
        assertTrue(withDebuff.operable)

        val withPercent = config.copy(toughness = ToughnessRestore.Percent(10.0))
        assertTrue(withPercent.operable)
    }
}
