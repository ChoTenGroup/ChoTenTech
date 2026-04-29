package io.github.zzzyyylllty.chotentech.feature

//
//
//@Awake(LifeCycle.ACTIVE)
//fun checkLevel26() {
//    submitAsync(period = 50) {
//        for((key, value) in playerRegions) {
//            if (value.contains("level-26")) {
//                val player = Bukkit.getPlayer(key.toUUID()) ?: continue
//
//                if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) continue
//                if (player.isDead) continue
//                val location = player.location
//                if (location.y <= 85 && player.world.getBlockAt(player.location).type.isWater()) {
//                    submit {
//                        player.addPotionEffect(PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1000, 12))
//                    }
//                }
//            }
//        }
//    }
//}
