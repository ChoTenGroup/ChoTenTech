package io.github.zzzyyylllty.chotentech.data

const val SANITY_REGEN = "sanity_regen"
const val SANITY_DRAIN = "sanity_drain"
const val STRESS_RESISTANCE = "stress_resistance"
const val STRESS_IMMUNITY = "stress_immunity"
const val FRACTURE = "fracture"
const val PAIN = "pain"
const val MUSCLE_WEAKNESS = "muscle_weakness"
const val PAIN_RELIEF = "pain_relief"
const val HOUND_VIRUS = "hound_virus"
const val IMMUNITY = "immunity"
const val STAMINA_REGEN = "stamina_regen"
const val PENETRATION = "penetration"
const val DIZZINESS = "dizziness"
const val SHOCK_SEQUELA = "shock_sequela"
const val MINOR_BLEED = "minor_bleed"
const val MAJOR_BLEED = "major_bleed"
const val WOUND = "wound"
const val TINNITUS = "tinnitus"
const val BLACKOUT = "blackout"
const val TREMOR = "tremor"
const val DEHYDRATION = "dehydration"
const val SEVERE_DEHYDRATION = "severe_dehydration"
const val FLASH = "flash"
val HEAD_ONLY_EFFECTS = setOf(DIZZINESS, SHOCK_SEQUELA, TINNITUS, BLACKOUT, PAIN_RELIEF,
    TREMOR, DEHYDRATION, SEVERE_DEHYDRATION, FLASH)

val STATUS_EFFECT_TYPES = listOf(
    SANITY_REGEN, SANITY_DRAIN, STRESS_RESISTANCE, STRESS_IMMUNITY,
    FRACTURE, PAIN, MUSCLE_WEAKNESS, PAIN_RELIEF, HOUND_VIRUS, IMMUNITY,
    STAMINA_REGEN, PENETRATION, DIZZINESS, SHOCK_SEQUELA, MINOR_BLEED, MAJOR_BLEED,
    WOUND, TINNITUS, BLACKOUT, TREMOR, DEHYDRATION, SEVERE_DEHYDRATION, FLASH
)
