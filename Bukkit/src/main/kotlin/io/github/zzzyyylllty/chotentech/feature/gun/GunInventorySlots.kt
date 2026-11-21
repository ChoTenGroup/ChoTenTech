package io.github.zzzyyylllty.chotentech.feature.gun

/**
 * PlayerInventory exposes armor and off-hand slots in its full size, but those are not
 * regular backpack storage slots. Gun inventory scans (ammo, magazines, and materials)
 * should use only the 36 storage slots.
 */
internal object GunInventorySlots {
    const val STORAGE_SIZE = 36

    fun storageSlots(inventorySize: Int): IntRange =
        0 until inventorySize.coerceIn(0, STORAGE_SIZE)
}
