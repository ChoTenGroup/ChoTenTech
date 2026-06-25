package io.github.zzzyyylllty.chotentech.listener

import io.github.zzzyyylllty.quetiapine.data.DataHelper
import io.github.zzzyyylllty.quetiapine.data.registerExternalData
import io.github.zzzyyylllty.quetiapine.function.launchText
import io.github.zzzyyylllty.quetiapine.util.ScriptHelper
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent

/**
 * Bridges @Awake and @SubscribeEvent from quetiapine package into chotentech's
 * TabooLib scan scope. Without this, those lifecycle hooks never fire.
 */
@Awake(LifeCycle.INIT)
fun bridgeQuetiapineScriptHelper() {
    ScriptHelper.initDependency()
}

@Awake(LifeCycle.ENABLE)
fun bridgeQuetiapineData() {
    registerExternalData()
    launchText()
}

@SubscribeEvent
fun bridgeQuetiapineDataHelperOnJoin(event: PlayerJoinEvent) {
    DataHelper.onJoin(event)
}
