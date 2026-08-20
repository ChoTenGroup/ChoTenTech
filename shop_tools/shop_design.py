# -*- coding: utf-8 -*-
"""商店全量重定价生成器：由数据表生成 shops/*.yml（40 个商店）。

规则：
- 每物品一个统一收购基准价（buy_money），卖出价 = round(40%)
- point 商店：buy_pt = round(buy/10), sell_pt = round(sell/10)
- 同基地上交任务物品只允许 sell:（收购），不允许 buy:
- 钢以上材料/装备一律不出售
- quest 解锁商品保留（重新定价），A+/S/P 级商品带限购
"""
import os

SHOP_DIR = r"E:\ChoTenTechbk\Bukkit\src\main\resources\shops"


# ============================================================
# 统一物品基准价（money，收购价 = 40%）
# ============================================================
def P(*pairs):
    return dict(pairs)

PRICE = P(
    # ---- 材料 ----
    ("wood", 30), ("stone", 25), ("plank", 20),
    ("nail", 30), ("screw", 30), ("gear", 45),
    ("dust", 5), ("trash_bag", 8),
    ("tape", 40), ("paper", 40), ("book", 140),
    ("matches", 40), ("lighter", 45), ("rope", 45),
    ("light_bulb", 480), ("battery", 480), ("battery_set", 480),
    ("capacitor", 480), ("motor", 480), ("drill", 480),
    ("wrench", 55), ("hammer", 140), ("engine_lubricating_oil", 300),
    ("copper_wire", 480), ("silver_wire", 480), ("precision_parts", 900),
    ("cpu", 900), ("power_supply", 480), ("ram_stick", 3000),
    ("vpx", 25000), ("cpufan", 480), ("disk", 900), ("u_disk", 480),
    ("pcb", 900), ("phone", 900), ("power_bank", 480),
    ("raspberry_pi", 900), ("radio", 3000), ("graphics_card", 3200),
    ("basic_wifi_sender", 480), ("advanced_wifi_sender", 480), ("wifi", 6000),
    ("basic_ai_core", 480), ("advanced_ai_core", 3400), ("pro_ai_core", 7000),
    ("3d_vision_glasses", 3000), ("hearing_aid", 3200),
    ("ultraviolet_lamp", 480), ("soundproof_panel", 480),
    ("record_player", 3300), ("sound_card", 3200), ("simple_watch", 900),
    ("examination_paper", 480),
    ("red_tape", 700), ("blue_tape", 700),
    ("rainbow_board", 800), ("green_board", 800),
    ("birthday_card", 750), ("stamp_collection", 750),
    # ---- 矿物 ----
    ("iron_ingot", 60), ("copper_ingot", 130), ("silver_ingot", 150),
    ("gold_ingot_low", 280), ("gold_ingot_medium", 340), ("gold_ingot_high", 900),
    ("bronze_ingot", 240), ("brass_ingot", 260), ("billon_ingot", 270),
    ("lead_ingot", 120), ("basic_alloy_ingot", 220), ("steel_ingot", 400),
    ("damascus_steel_ingot", 1200),
    ("coal", 110), ("charcoal", 100), ("flint", 100), ("flint_sharp", 300),
    ("clay_ball", 130), ("carbon", 140),
    ("raw_iron", 120), ("raw_copper", 110), ("raw_gold", 150), ("raw_coal", 100),
    # ---- 宝石（仅收购） ----
    ("diamond", 1600), ("ruby", 1400), ("sapphire", 1400), ("emerald", 1300),
    ("black_diamond", 1800), ("mythril_ingot", 2500),
    ("ultra_alloy_ingot", 15000), ("rainbow_alloy_ingot", 12000),
    ("synthetic_diamond", 380), ("synthetic_ruby", 360),
    ("synthetic_sapphire", 360), ("synthetic_emerald", 350),
    # ---- 食物 ----
    ("bread", 60), ("cracker", 50), ("potato_chips", 125),
    ("chocolate", 130), ("muffin", 130), ("oreo", 130), ("popcorn", 125),
    ("pie", 135), ("cola", 135), ("sprite", 135), ("kola", 260),
    ("cola_nosugar", 135), ("twofacesoda", 260), ("rose", 250),
    ("soda", 50), ("natto_flavored_cola", 55), ("max_noodles", 280),
    ("ice_tea", 150), ("mongolia_tea", 280), ("manba_tea", 280),
    ("milk", 130), ("milk_pack_tall", 150), ("luck_milk_banana", 150),
    ("pizza", 300), ("pizza_soup", 45), ("taco", 300),
    ("barbecue", 320), ("medium_rare_steak", 340), ("brazilian_barbecue", 360),
    ("herring_can", 300), ("scits", 140), ("scits_can", 300),
    ("konjac", 55), ("konjac_shuang", 160),
    ("emergency_water_pack", 160), ("oatmeal", 50), ("bread_cubes", 55),
    ("honey", 280), ("royal_jelly", 280), ("jerky", 150),
    ("french_fries", 160), ("sugar_cube", 40), ("mayonnaise", 130),
    ("waffle", 130), ("apple_can", 150), ("bean_can", 150), ("beef_can", 280),
    ("fish_can", 145), ("mushroom_can", 145), ("orange_can", 150),
    ("pineapple_can", 155), ("potato_can", 145), ("sweetcorn_can", 150),
    ("tomato_can", 150), ("mre", 320),
    ("candy_almond_mints", 1600), ("candy_biting_bullets", 1600),
    ("candy_guns", 1650), ("candy_brainiacs", 1700),
    ("candy_silver_tongues", 1700), ("candy_hazardous_waste", 1800),
    ("candy_stanleys", 2000),
    ("royal_rations", 2000), ("object_100", 3000), ("golden_necklace", 2500),
    ("katana", 480), ("pro_gold_bar", 15000),
    # ---- 食材 ----
    ("wheat", 110), ("carrot", 110), ("potato", 110),
    ("porkchop", 130), ("beef", 140), ("chicken", 125), ("mutton", 130),
    ("sugar_cane", 115), ("cocoa_beans", 120), ("apple", 115),
    ("berry", 30), ("plum", 30), ("strawberry", 30),
    ("brown_mushroom", 130), ("red_mushroom", 130), ("spider_leg", 40),
    # ---- 熟食 ----
    ("cooked_porkchop", 160), ("cooked_beef", 170), ("cooked_chicken", 160),
    ("cooked_mutton", 165), ("cooked_potato", 140), ("cooked_carrot", 135),
    ("cooked_beetroot", 140), ("cooked_cactus", 145),
    ("cooked_brown_mushroom", 140), ("cooked_red_mushroom", 140),
    ("cooked_spider_leg", 45), ("baked_potato", 140),
    # ---- 医疗 ----
    ("bandage", 240), ("sterile_bandage", 260), ("painkiller_pills", 160),
    ("almond_bandage", 180), ("antibiotic", 300), ("insulin", 950),
    ("ice_medkit", 160), ("tianlong_medkit", 300), ("onefirst_medkit", 1500),
    ("zaped", 1000), ("epsilon_pills", 900),
    ("sql_pills_aqua", 900), ("sql_pills_purple", 900), ("6ch_vaseline", 300),
    ("akacandy_medkit", 12000),
    ("almond_water_blue", 300), ("almond_water_red", 300),
    ("almond_water_green", 300), ("almond_water_gray", 300),
    ("boiled_almond_water", 320),
    ("adrenaline_injector", 320), ("etg_injector", 900),
    ("fast_stamina_injector", 900), ("fast_strength_injector", 900),
    ("flower_of_fire_injector", 320), ("golden_apple_injector", 1800),
    ("inocentia_injector", 1800), ("rainbow_injector", 8000),
    ("transfer_stamina_injector", 900), ("zako_healer_injector", 320),
    ("mule_injector", 900), ("empty_injector", 140),
    # ---- 掉落物 ----
    ("bone", 35), ("hound_paw", 45), ("scale", 45), ("smilers_head", 300),
    ("bursters_shell", 700), ("spider_silk", 140), ("deathmoths_mucus", 45),
    ("dullers_core", 800), ("dullers_tissue", 800),
    ("leather_unprocessed", 45), ("fibre", 35), ("plastic", 40),
    ("hard_plastic", 160), ("glass_bottle", 40), ("glass_material", 45),
    ("fabric_skin", 150), ("rag", 40), ("broken_disk", 40), ("flat_stone", 40),
    # ---- 军火（黑市） ----
    ("gunpowder_kangel", 600), ("gunpowder_player", 300), ("gunpowder_rain", 600),
    ("thermite", 200), ("atomic_gunpowder", 320),
    ("6ch_grenade", 200), ("shakalaka_grenade", 350), ("b_shakalaka_grenade", 750),
    ("hqlm_petrol", 180), ("firesalt_petrol", 320), ("strong_firesalt_petrol", 700),
    ("frontroad_grenade", 190), ("endroad_grenade", 330),
    ("nizxc_flash", 60), ("zx_stun", 60), ("ice_stun", 60), ("shengguang_grenade", 60),
    # ---- 酒水 ----
    ("vodka", 300), ("moonshine", 600), ("beer", 140), ("rio", 140),
    ("suntory_whisky", 320), ("welcome_wine", 300), ("tno_noodles", 300),
)

# 装备基准价（buy money，sell = 40%）
EQUIP = P(
    ("leather_helmet", 90), ("leather_chestplate", 120),
    ("leather_leggings", 105), ("leather_boots", 80),
    ("flint_sword", 120), ("flint_axe", 110), ("flint_pickaxe", 110),
    ("flint_shovel", 100), ("flint_hoe", 100), ("flint_dagger", 100),
    ("flint_knife", 100), ("flint_mattock", 105), ("flint_combataxe", 115),
    ("flint_handaxe", 105), ("flint_hammer", 105),
    ("bronze_sword", 2000), ("bronze_helmet", 1800), ("bronze_chestplate", 2400),
    ("bronze_leggings", 2100), ("bronze_boots", 1600),
    ("bronze_pickaxe", 1800), ("bronze_axe", 1900), ("bronze_shovel", 1700),
    ("bronze_hoe", 1700), ("bronze_dagger", 1800), ("bronze_knife", 1800),
    ("bronze_mattock", 1900), ("bronze_combataxe", 2000), ("bronze_handaxe", 1800),
    ("bronze_hammer", 1900),
    ("gold_sword", 2400), ("gold_helmet", 2200), ("gold_chestplate", 2800),
    ("gold_leggings", 2500), ("gold_boots", 2000),
    ("gold_pickaxe", 2200), ("gold_axe", 2300), ("gold_shovel", 2100),
    ("gold_hoe", 2100), ("gold_dagger", 2200), ("gold_knife", 2200),
    ("gold_mattock", 2300), ("gold_combataxe", 2400), ("gold_handaxe", 2200),
    ("gold_hammer", 2300),
    ("iron_sword", 8000), ("iron_helmet", 7200), ("iron_chestplate", 9600),
    ("iron_leggings", 8400), ("iron_boots", 6400),
    ("iron_pickaxe", 6800), ("iron_axe", 7200), ("iron_shovel", 6400),
    ("iron_hoe", 6400), ("iron_dagger", 7200), ("iron_knife", 7200),
    ("iron_mattock", 7600), ("iron_combataxe", 8000), ("iron_handaxe", 7200),
    ("iron_hammer", 7600),
    ("basic_alloy_sword", 11000), ("basic_alloy_helmet", 9900),
    ("basic_alloy_chestplate", 13200), ("basic_alloy_leggings", 11550),
    ("basic_alloy_boots", 8800),
    ("basic_alloy_pickaxe", 9350), ("basic_alloy_axe", 9900),
    ("basic_alloy_shovel", 8800), ("basic_alloy_hoe", 8800),
    ("basic_alloy_dagger", 9900), ("basic_alloy_knife", 9900),
    ("basic_alloy_mattock", 10450), ("basic_alloy_combataxe", 11000),
    ("basic_alloy_handaxe", 9900), ("basic_alloy_hammer", 10450),
    ("steel_sword", 15000), ("steel_helmet", 13500), ("steel_chestplate", 18000),
    ("steel_leggings", 15750), ("steel_boots", 12000),
    ("steel_pickaxe", 12750), ("steel_axe", 13500), ("steel_shovel", 12000),
    ("steel_hoe", 12000), ("steel_dagger", 13500), ("steel_knife", 13500),
    ("steel_mattock", 14250), ("steel_combataxe", 15000), ("steel_handaxe", 13500),
    ("steel_hammer", 14250),
)

ALL_PRICE = dict(PRICE)
ALL_PRICE.update(EQUIP)

QUEST_DISPLAY = {
    "TaiKuLa": "太苦啦", "PenicillinAction": "青霉素行动",
    "VaccineResearch": "疫苗研究", "MaterialSupply": "材料供应",
    "NianZhangShi": "念张师", "OldIceBBQ": "老冰烧烤",
    "XiangLiCar": "箱里车", "AuxTraining": "辅助训练",
    "Capable": "有能力的", "IndependentArmy": "独立武装",
    "UncommonAbility4": "非常能力·四", "DriftNorth": "向北漂流",
    "PursuingPerformance": "追求性能", "Panopticoal4": "煤炭监狱·四",
    "ClawMachinePower": "爪机力量", "TakeOffPlane": "Take Off 飞机",
    "DontLaughChallenge1": "不许笑挑战 I", "WorkForFuture4": "为了今后的工作 - IV",
    "CherryBlossom": "樱花树", "SecretGoods": "秘密货物",
    "FacingDespair": "直面绝望", "TrainTravel3": "火车旅行·三",
    "AdultChoice": "成人的选择", "DutyLeave": "休假责任",
    "ScrapMetal": "废金属", "Rocket": "火箭", "FlashGoldCharge": "闪金冲锋",
    "QingQingShi": "青青石", "InternetTravel2": "互联网之旅 II",
    "RunningToTheNight": "向着夜奔跑", "ShenJin": "沈金",
    "JiFangWeiHu": "机房维护", "NewTypeOfMedication": "新型药品",
}

# 条目: (item_id, mode, gate, quest, limit)  mode: b=仅售 s=仅收购 bs=售+收
# gate: 0/2/3 = 无/Lv.2/Lv.3   quest: 任务ID或None   limit: 24h 限购数或0
def E(item, mode="bs", gate=0, quest=None, limit=0):
    return (item, mode, gate, quest, limit)


def _sec(title, *entries):
    return (title, entries)


# ============================================================
# 商店数据
# ============================================================
SHOPS = {}

SHOPS["ZhiChen"] = {
    "name": '<gold>知陈', "currency": "money",
    "sections": [
        _sec("Lv.1 - 杂货", E("coal"), E("charcoal"), E("flint"),
             E("matches"), E("lighter"), E("tape")),
        _sec("Lv.1 - 材料回收",
             E("raw_iron", "s"), E("raw_copper", "s"), E("raw_gold", "s"),
             E("bone", "s"), E("hound_paw", "s")),
        _sec("Lv.2 - 燧石装备",
             E("flint_sword", "b", 2), E("flint_pickaxe", "b", 2),
             E("flint_axe", "b", 2), E("flint_shovel", "b", 2)),
        _sec("Lv.2 - 皮革装备",
             E("leather_helmet", "b", 2), E("leather_chestplate", "b", 2),
             E("leather_leggings", "b", 2), E("leather_boots", "b", 2)),
        _sec("Lv.3 - 铁制装备",
             E("iron_sword", "b", 3), E("iron_helmet", "b", 3),
             E("iron_chestplate", "b", 3), E("iron_leggings", "b", 3),
             E("iron_boots", "b", 3)),
        _sec("Lv.3 - 解锁商品",
             E("damascus_steel_ingot", "b", 0, "ScrapMetal", 1)),
    ],
}

SHOPS["BlackSmith"] = {
    "name": '<gold>铁匠', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础材料",
             E("coal"), E("charcoal"), E("flint"), E("clay_ball"), E("carbon")),
        _sec("Lv.1 - 锭材",
             E("bronze_ingot"), E("brass_ingot"), E("billon_ingot"),
             E("lead_ingot"), E("silver_ingot"), E("gold_ingot_low"),
             E("gold_ingot_medium"), E("basic_alloy_ingot"), E("steel_ingot")),
        _sec("Lv.1 - 材料回收",
             E("wood", "s"), E("stone", "s"), E("iron_ingot", "s"),
             E("raw_iron", "s"), E("raw_copper", "s"), E("raw_gold", "s"),
             E("raw_coal", "s")),
        _sec("Lv.2 - 基础装备",
             E("leather_helmet"), E("leather_chestplate"),
             E("leather_leggings"), E("leather_boots"),
             E("flint_sword", "b", 2), E("flint_pickaxe", "b", 2),
             E("flint_axe", "b", 2)),
        _sec("Lv.2 - 青铜与金装备",
             E("bronze_sword", "b", 2), E("bronze_chestplate", "b", 2),
             E("bronze_leggings", "b", 2), E("bronze_boots", "b", 2),
             E("gold_sword", "b", 2), E("gold_chestplate", "b", 2),
             E("gold_leggings", "b", 2), E("gold_boots", "b", 2)),
        _sec("Lv.3 - 铁制装备",
             E("iron_sword", "b", 3), E("iron_helmet", "b", 3),
             E("iron_leggings", "b", 3), E("iron_boots", "b", 3),
             E("iron_axe", "b", 3)),
        _sec("Lv.3 - 高级合金装备",
             E("basic_alloy_sword", "b", 3), E("basic_alloy_chestplate", "b", 3),
             E("basic_alloy_leggings", "b", 3), E("basic_alloy_helmet", "b", 3),
             E("basic_alloy_boots", "b", 3),
             E("steel_sword", "b", 3), E("steel_helmet", "b", 3),
             E("steel_leggings", "b", 3), E("steel_boots", "b", 3),
             E("steel_pickaxe", "b", 3)),
        _sec("Lv.3 - 解锁商品",
             E("iron_chestplate", "b", 3, "MaterialSupply", 1)),
    ],
}

SHOPS["Adon"] = {
    "name": '<gold>亚当', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础电子零件",
             E("light_bulb"), E("cpufan"), E("u_disk"), E("power_bank"),
             E("raspberry_pi"), E("pcb")),
        _sec("Lv.1 - 旧件回收",
             E("screw", "s"), E("nail", "s"), E("broken_disk", "s"),
             E("tape", "s"), E("plastic", "s"), E("glass_bottle", "s")),
        _sec("Lv.2 - 电子元件",
             E("ram_stick", "bs", 2), E("disk", "bs", 2), E("motor", "bs", 2),
             E("drill", "bs", 2), E("phone", "bs", 2)),
        _sec("Lv.2 - 精选设备（仅售）",
             E("basic_wifi_sender", "b", 2), E("advanced_wifi_sender", "b", 2),
             E("pro_ai_core", "b", 2, None, 1)),
        _sec("Lv.3 - 高级电子设备",
             E("vpx", "bs", 3, None, 2), E("radio", "bs", 3, None, 3),
             E("graphics_card", "b", 3, None, 2), E("wifi", "b", 3, None, 1)),
        _sec("Lv.3 - 解锁商品",
             E("moonshine", "b", 0, "TaiKuLa", 2), E("vodka", "b", 0, "TaiKuLa", 3)),
    ],
}

SHOPS["BoFeng"] = {
    "name": '<gold>波风', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础食品",
             E("pizza_soup"), E("soda"), E("oatmeal"), E("bread_cubes"),
             E("konjac"), E("emergency_water_pack")),
        _sec("Lv.1 - 食材收购",
             E("wheat", "s"), E("carrot", "s"), E("potato", "s"),
             E("porkchop", "s"), E("beef", "s"), E("chicken", "s"),
             E("mutton", "s"), E("sugar_cane", "s"), E("cocoa_beans", "s"),
             E("brown_mushroom", "s"), E("red_mushroom", "s"),
             E("spider_leg", "s"), E("berry", "s")),
        _sec("Lv.2 - 家常菜",
             E("cooked_porkchop", "b", 2), E("cooked_beef", "b", 2),
             E("cooked_chicken", "b", 2), E("cooked_mutton", "b", 2),
             E("cooked_potato", "b", 2), E("cooked_carrot", "b", 2),
             E("cooked_beetroot", "b", 2), E("cooked_cactus", "b", 2),
             E("cooked_brown_mushroom", "b", 2), E("cooked_red_mushroom", "b", 2),
             E("baked_potato", "b", 2), E("scits", "b", 2), E("jerky", "b", 2)),
        _sec("Lv.3 - 高级食品",
             E("honey", "b", 3), E("royal_jelly", "b", 3),
             E("herring_can", "b", 3), E("scits_can", "b", 3),
             E("manba_tea", "b", 3), E("konjac_shuang", "b", 3)),
        _sec("Lv.3 - 珍稀糖果",
             E("candy_almond_mints", "b", 3, None, 1)),
        _sec("Lv.3 - 解锁商品",
             E("pro_gold_bar", "b", 0, "NianZhangShi", 1)),
    ],
}

SHOPS["Kael"] = {
    "name": '<green>凯尔', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础医疗",
             E("painkiller_pills"), E("almond_bandage"), E("ice_medkit"),
             E("empty_injector")),
        _sec("Lv.1 - 医疗材料收购",
             E("rag", "s"), E("fabric_skin", "s"), E("glass_bottle", "s"),
             E("deathmoths_mucus", "s"), E("spider_silk", "s")),
        _sec("Lv.2 - 进阶医疗",
             E("antibiotic", "bs", 2, "PenicillinAction"),
             E("tianlong_medkit", "bs", 2), E("6ch_vaseline", "bs", 2),
             E("adrenaline_injector", "bs", 2), E("flower_of_fire_injector", "bs", 2),
             E("zako_healer_injector", "bs", 2)),
        _sec("Lv.3 - 高级医疗",
             E("insulin", "bs", 3, None, 3), E("onefirst_medkit", "bs", 3, None, 2),
             E("zaped", "bs", 3, None, 2), E("epsilon_pills", "bs", 3, None, 2),
             E("sql_pills_aqua", "bs", 3, None, 2), E("sql_pills_purple", "bs", 3, None, 2),
             E("etg_injector", "bs", 3, None, 2), E("fast_stamina_injector", "bs", 3, None, 2),
             E("fast_strength_injector", "bs", 3, None, 2),
             E("transfer_stamina_injector", "bs", 3, None, 2),
             E("mule_injector", "bs", 3, None, 2),
             E("golden_apple_injector", "bs", 3, None, 1),
             E("inocentia_injector", "bs", 3, None, 1),
             E("rainbow_injector", "bs", 3, None, 1)),
    ],
}

SHOPS["Lefty"] = {
    "name": '<gold>小左', "currency": "money",
    "sections": [
        _sec("Lv.1 - 零件回收",
             E("screw", "s"), E("nail", "s"), E("gear", "s"),
             E("motor", "s"), E("copper_wire", "s"), E("battery", "s")),
        _sec("Lv.2 - 武器",
             E("iron_sword", "b", 2)),
        _sec("Lv.3 - 高级设备",
             E("graphics_card", "b", 3, None, 2),
             E("advanced_ai_core", "b", 0, "DriftNorth", 1)),
    ],
}

SHOPS["MaoSe"] = {
    "name": '<gold>毛瑟', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础军备",
             E("flint"), E("leather_helmet"), E("leather_chestplate"),
             E("leather_leggings"), E("leather_boots"),
             E("flint_sword"), E("flint_pickaxe"), E("flint_axe")),
        _sec("Lv.1 - 材料收购",
             E("bone", "s"), E("spider_silk", "s"),
             E("leather_unprocessed", "s")),
        _sec("Lv.2 - 青铜与金装备",
             E("bronze_sword", "b", 2), E("bronze_chestplate", "b", 2),
             E("bronze_boots", "b", 2),
             E("gold_helmet", "b", 2), E("gold_chestplate", "b", 2),
             E("gold_leggings", "b", 2), E("gold_boots", "b", 2)),
        _sec("Lv.3 - 铁制装备",
             E("iron_helmet", "b", 3), E("iron_chestplate", "b", 3),
             E("iron_leggings", "b", 3), E("iron_boots", "b", 3)),
        _sec("Lv.3 - 钢制装备",
             E("steel_sword", "b", 3), E("steel_helmet", "b", 3),
             E("steel_chestplate", "b", 3), E("steel_leggings", "b", 3),
             E("steel_boots", "b", 3)),
        _sec("Lv.3 - 解锁商品",
             E("iron_sword", "b", 0, "IndependentArmy", 1)),
    ],
}

SHOPS["MinZe"] = {
    "name": '<gold>悯泽', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础矿物",
             E("silver_ingot"), E("gold_ingot_low"), E("gold_ingot_medium"),
             E("bronze_ingot"), E("lead_ingot")),
        _sec("Lv.1 - 研究材料收购",
             E("examination_paper", "s"), E("disk", "s"), E("u_disk", "s"),
             E("basic_ai_core", "s"), E("dullers_core", "s")),
        _sec("Lv.2 - 研究设备",
             E("steel_ingot", "b", 2), E("ultraviolet_lamp", "b", 2),
             E("soundproof_panel", "b", 2), E("raspberry_pi", "b", 2),
             E("hearing_aid", "b", 2, None, 2), E("3d_vision_glasses", "b", 2, None, 2),
             E("record_player", "b", 2, None, 2)),
        _sec("Lv.3 - 高级研究",
             E("pro_ai_core", "b", 0, "TakeOffPlane", 1)),
    ],
}

SHOPS["Rhodes"] = {
    "name": '<gold>罗德斯', "currency": "money",
    "sections": [
        _sec("Lv.1 - 军需补给",
             E("emergency_water_pack"), E("almond_bandage")),
        _sec("Lv.1 - 材料回收",
             E("fibre", "s"), E("plastic", "s"), E("glass_material", "s")),
        _sec("Lv.2 - 铁制装备",
             E("iron_sword", "b", 2), E("iron_helmet", "b", 2),
             E("iron_chestplate", "b", 2), E("iron_leggings", "b", 2),
             E("iron_boots", "b", 2)),
        _sec("Lv.3 - 钢制装备",
             E("steel_sword", "b", 3), E("steel_chestplate", "b", 3)),
        _sec("Lv.3 - 解锁商品",
             E("onefirst_medkit", "b", 3, "FacingDespair", 1)),
    ],
}

SHOPS["Sylvia"] = {
    "name": '<gold>西尔维亚', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础设备",
             E("examination_paper"), E("simple_watch")),
        _sec("Lv.2 - 通信设备",
             E("basic_wifi_sender", "b", 2), E("advanced_wifi_sender", "b", 2),
             E("radio", "b", 2, None, 3), E("ram_stick", "b", 2, None, 3),
             E("wifi", "b", 2, None, 1), E("phone", "b", 2)),
        _sec("Lv.3 - 高级设备",
             E("sound_card", "b", 3, None, 2), E("hearing_aid", "b", 3, None, 2),
             E("ultraviolet_lamp", "b", 3), E("soundproof_panel", "b", 3),
             E("record_player", "b", 3, None, 2), E("3d_vision_glasses", "b", 3, None, 2)),
    ],
}

SHOPS["Vance"] = {
    "name": '<gold>万斯', "currency": "money",
    "sections": [
        _sec("Lv.1 - 补给品",
             E("jerky"), E("emergency_water_pack"), E("oatmeal"),
             E("bread_cubes"), E("scits"), E("konjac")),
        _sec("Lv.1 - 日用品",
             E("light_bulb"), E("matches")),
        _sec("Lv.1 - 食材收购",
             E("wheat", "s"), E("carrot", "s"), E("potato", "s"),
             E("porkchop", "s"), E("beef", "s"), E("chicken", "s"),
             E("sugar_cane", "s"), E("cocoa_beans", "s"), E("apple", "s"),
             E("berry", "s")),
        _sec("Lv.2 - 精选补给",
             E("herring_can", "b", 2), E("honey", "b", 2), E("royal_jelly", "b", 2),
             E("konjac_shuang", "b", 2), E("scits_can", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("royal_rations", "b", 0, "AdultChoice", 1)),
    ],
}

SHOPS["Zero"] = {
    "name": '<gold>零', "currency": "money",
    "sections": [
        _sec("Lv.1 - 收藏品",
             E("examination_paper")),
        _sec("Lv.1 - 旧物收购",
             E("rag", "s"), E("dust", "s"), E("broken_disk", "s"),
             E("screw", "s"), E("nail", "s")),
        _sec("Lv.2 - 酒水",
             E("vodka", "b", 2), E("beer", "b", 2), E("rio", "b", 2),
             E("moonshine", "b", 2, None, 3), E("suntory_whisky", "b", 2),
             E("welcome_wine", "b", 2), E("tno_noodles", "b", 2)),
        _sec("Lv.3 - 收藏品",
             E("stamp_collection", "b", 3, None, 2), E("birthday_card", "b", 3, None, 2),
             E("rainbow_board", "b", 3, None, 2)),
        _sec("Lv.3 - 解锁商品",
             E("advanced_wifi_sender", "b", 0, "ScrapMetal", 2)),
    ],
}

SHOPS["HaLei"] = {
    "name": '<gold>哈雷', "currency": "money",
    "sections": [
        _sec("Lv.1 - 武器",
             E("bronze_sword"), E("bronze_chestplate"), E("bronze_boots")),
        _sec("Lv.1 - 掉落物收购",
             E("bone", "s"), E("hound_paw", "s"), E("smilers_head", "s"),
             E("scale", "s"), E("bursters_shell", "s")),
        _sec("Lv.2 - 金装备",
             E("gold_sword", "b", 2), E("gold_chestplate", "b", 2),
             E("gold_leggings", "b", 2), E("gold_boots", "b", 2)),
        _sec("Lv.3 - 铁制装备",
             E("iron_sword", "b", 3), E("iron_helmet", "b", 3),
             E("iron_chestplate", "b", 3), E("iron_leggings", "b", 3),
             E("iron_boots", "b", 3)),
        _sec("Lv.3 - 钢制装备",
             E("steel_sword", "b", 3), E("steel_boots", "b", 3)),
        _sec("Lv.3 - 解锁商品",
             E("steel_chestplate", "b", 3, "XiangLiCar", 1)),
    ],
}

SHOPS["Ice"] = {
    "name": '<gold>冰块', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础食材收购",
             E("porkchop", "s"), E("beef", "s"), E("chicken", "s"),
             E("brown_mushroom", "s"), E("red_mushroom", "s"), E("potato", "s"),
             E("spider_leg", "s"), E("berry", "s")),
        _sec("Lv.1 - 烧烤",
             E("barbecue"), E("cooked_porkchop"), E("cooked_beef"),
             E("cooked_chicken"), E("cooked_mutton")),
        _sec("Lv.2 - 特色烧烤",
             E("medium_rare_steak", "b", 2), E("taco", "b", 2),
             E("herring_can", "b", 2), E("cooked_cactus", "b", 2),
             E("cooked_brown_mushroom", "b", 2), E("cooked_red_mushroom", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("brazilian_barbecue", "b", 3, "OldIceBBQ", 1)),
    ],
}

SHOPS["JiShi"] = {
    "name": '<gold>技师', "currency": "money",
    "sections": [
        _sec("Lv.1 - 后勤物资",
             E("iron_ingot"), E("coal"), E("flint"), E("bandage"),
             E("sterile_bandage")),
        _sec("Lv.1 - 掉落物收购",
             E("bone", "s"), E("hound_paw", "s"), E("smilers_head", "s"),
             E("scale", "s"), E("bursters_shell", "s"), E("dullers_core", "s"),
             E("dullers_tissue", "s"), E("deathmoths_mucus", "s")),
        _sec("Lv.2 - 材料",
             E("gold_ingot_medium", "b", 2), E("steel_ingot", "b", 2)),
        _sec("Lv.2 - 装备",
             E("iron_sword", "b", 2), E("iron_chestplate", "b", 2),
             E("steel_sword", "b", 2)),
        _sec("Lv.3 - 医疗",
             E("onefirst_medkit", "b", 3, None, 2)),
        _sec("Lv.3 - 解锁商品",
             E("steel_chestplate", "b", 3, "Capable", 1)),
    ],
}

SHOPS["Luka"] = {
    "name": '<gold>卢卡', "currency": "money",
    "sections": [
        _sec("Lv.1 - 零食",
             E("chocolate"), E("cracker"), E("popcorn"), E("muffin"),
             E("oreo"), E("pie"), E("potato_chips")),
        _sec("Lv.1 - 旧物收购",
             E("paper", "s"), E("tape", "s"), E("battery", "s"), E("nail", "s")),
        _sec("Lv.2 - 饮料",
             E("cola", "b", 2), E("sprite", "b", 2), E("kola", "b", 2),
             E("twofacesoda", "b", 2), E("rose", "b", 2), E("max_noodles", "b", 2)),
        _sec("Lv.3 - 珍稀糖果",
             E("candy_almond_mints", "b", 3, None, 1),
             E("candy_biting_bullets", "b", 3, None, 1),
             E("candy_guns", "b", 3, None, 1),
             E("candy_brainiacs", "b", 3, None, 1),
             E("candy_silver_tongues", "b", 3, None, 1),
             E("candy_hazardous_waste", "b", 3, None, 1)),
        _sec("Lv.3 - 解锁商品",
             E("candy_stanleys", "b", 0, "ClawMachinePower", 1)),
    ],
}

SHOPS["ZhongHai"] = {
    "name": '<gold>钟海', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础物资",
             E("bandage"), E("sterile_bandage"), E("bread"), E("cracker"),
             E("painkiller_pills")),
        _sec("Lv.1 - 掉落物收购",
             E("bone", "s"), E("hound_paw", "s"), E("smilers_head", "s"),
             E("scale", "s")),
        _sec("Lv.2 - 材料",
             E("gold_ingot_medium", "b", 2), E("steel_ingot", "b", 2)),
        _sec("Lv.2 - 装备",
             E("iron_sword", "b", 2), E("iron_chestplate", "b", 2),
             E("iron_boots", "b", 2)),
        _sec("Lv.3 - 医疗",
             E("onefirst_medkit", "b", 3, None, 2)),
        _sec("Lv.3 - 解锁商品",
             E("steel_chestplate", "b", 3, "UncommonAbility4", 1)),
    ],
}

SHOPS["xite"] = {
    "name": '<gold>西特', "currency": "money",
    "sections": [
        _sec("Lv.1 - 综合物资",
             E("bandage"), E("bread"), E("wood"), E("cracker"), E("tape"),
             E("almond_water_gray"), E("painkiller_pills"),
             E("sterile_bandage"), E("mre")),
        _sec("Lv.1 - 掉落物收购",
             E("bone", "s"), E("hound_paw", "s"), E("plastic", "s"), E("rag", "s")),
        _sec("Lv.2 - 铁制装备",
             E("iron_sword", "b", 2), E("iron_helmet", "b", 2),
             E("iron_chestplate", "b", 2), E("iron_leggings", "b", 2),
             E("iron_boots", "b", 2)),
        _sec("Lv.3 - 钢制装备",
             E("steel_sword", "b", 3), E("steel_chestplate", "b", 3)),
    ],
}

SHOPS["AkaCandy"] = {
    "name": '<gold>阿卡糖果', "currency": "money",
    "sections": [
        _sec("Lv.1 - 基础物资",
             E("bread"), E("cracker"), E("chocolate"), E("bandage"),
             E("painkiller_pills")),
        _sec("Lv.1 - 旧物收购",
             E("plastic", "s"), E("rag", "s"), E("bone", "s"), E("nail", "s"),
             E("screw", "s")),
        _sec("Lv.2 - 医疗",
             E("sterile_bandage", "b", 2), E("antibiotic", "b", 2),
             E("almond_water_blue", "b", 2), E("almond_water_red", "b", 2)),
        _sec("Lv.2 - 口粮",
             E("apple_can", "b", 2), E("mre", "b", 2)),
    ],
}

SHOPS["AmeRain"] = {
    "name": '<gold>阿梅雨', "currency": "money",
    "sections": [
        _sec("Lv.1 - 网络设备",
             E("battery"), E("copper_wire"), E("cpu")),
        _sec("Lv.1 - 旧件收购",
             E("plastic", "s"), E("hard_plastic", "s"), E("broken_disk", "s"),
             E("screw", "s"), E("nail", "s"),
             E("raw_iron", "s"), E("raw_copper", "s"), E("raw_gold", "s"),
             E("raw_coal", "s")),
        _sec("Lv.2 - 通信设备",
             E("advanced_wifi_sender", "b", 2), E("radio", "b", 2, None, 3),
             E("phone", "b", 2)),
        _sec("Lv.2 - 电子元件",
             E("graphics_card", "b", 2, None, 2),
             E("3d_vision_glasses", "b", 2, None, 2)),
        _sec("Lv.3 - 高级设备",
             E("pro_ai_core", "b", 3, None, 1)),
        _sec("Lv.3 - 解锁商品",
             E("advanced_ai_core", "b", 0, "InternetTravel2", 1)),
    ],
}

SHOPS["TheAchu"] = {
    "name": '<gold>阿初', "currency": "money",
    "sections": [
        _sec("Lv.1 - 杏仁水",
             E("almond_water_blue"), E("almond_water_green"),
             E("almond_water_red"), E("bandage"), E("bread")),
        _sec("Lv.1 - 瓶材收购",
             E("glass_bottle", "s"), E("rag", "s"), E("leather_unprocessed", "s")),
        _sec("Lv.2 - 进阶杏仁水",
             E("boiled_almond_water", "b", 2), E("sterile_bandage", "b", 2),
             E("painkiller_pills", "b", 2)),
        _sec("Lv.2 - 口粮",
             E("apple_can", "b", 2), E("mre", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("object_100", "b", 0, "TrainTravel3", 1)),
    ],
}

SHOPS["FangHuo"] = {
    "name": '<gold>房火', "currency": "money",
    "sections": [
        _sec("Lv.1 - 主播用品",
             E("red_tape", "b", 0, None, 2), E("blue_tape", "b", 0, None, 2),
             E("disk"), E("rainbow_board", "b", 0, None, 2),
             E("green_board", "b", 0, None, 2)),
        _sec("Lv.1 - 旧物收购",
             E("tape", "s"), E("dust", "s"), E("broken_disk", "s"), E("rag", "s")),
        _sec("Lv.2 - 基础物资",
             E("bandage", "b", 2), E("bread", "b", 2), E("mre", "b", 2),
             E("iron_ingot", "b", 2), E("iron_sword", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("advanced_ai_core", "b", 0, "QingQingShi", 1)),
    ],
}

SHOPS["QianXia"] = {
    "name": '<gold>千夏', "currency": "money",
    "sections": [
        _sec("Lv.1 - 带货零食",
             E("sprite"), E("kola"), E("ice_tea"), E("twofacesoda"),
             E("rose"), E("soda")),
        _sec("Lv.1 - 食材收购",
             E("wheat", "s"), E("sugar_cane", "s"), E("cocoa_beans", "s"),
             E("apple", "s"), E("berry", "s")),
        _sec("Lv.2 - 物资",
             E("iron_ingot", "b", 2), E("mre", "b", 2),
             E("onefirst_medkit", "b", 2, None, 2)),
        _sec("Lv.3 - 解锁商品",
             E("object_100", "b", 0, "DontLaughChallenge1", 1)),
    ],
}

SHOPS["AkaRain"] = {
    "name": '<gold>阿卡雨', "currency": "point",
    "sections": [
        _sec("Lv.1 - 基础食品",
             E("bread"), E("cracker"), E("chocolate"), E("muffin")),
        _sec("Lv.1 - 旧物收购",
             E("paper", "s"), E("tape", "s"), E("screw", "s"), E("nail", "s"),
             E("rag", "s"), E("dust", "s"), E("flint", "s")),
        _sec("Lv.2 - 饮料",
             E("cola", "b", 2), E("apple_can", "b", 2),
             E("almond_water_blue", "b", 2), E("almond_water_green", "b", 2)),
        _sec("Lv.2 - 医疗",
             E("bandage", "b", 2), E("sterile_bandage", "b", 2),
             E("painkiller_pills", "b", 2)),
    ],
}

SHOPS["HeDe"] = {
    "name": '<gold>赫德', "currency": "point",
    "sections": [
        _sec("Lv.1 - 培训物资",
             E("bandage"), E("bread"), E("painkiller_pills"),
             E("sterile_bandage")),
        _sec("Lv.1 - 旧物收购",
             E("rag", "s")),
        _sec("Lv.2 - 训练物资",
             E("iron_ingot", "b", 2), E("iron_sword", "b", 2),
             E("mre", "b", 2), E("antibiotic", "b", 2), E("book", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("onefirst_medkit", "b", 3, "AuxTraining", 1)),
    ],
}

SHOPS["Lemon"] = {
    "name": '<gold>柠檬', "currency": "point",
    "sections": [
        _sec("Lv.1 - 皮革装备",
             E("leather_helmet"), E("leather_chestplate"),
             E("leather_leggings"), E("leather_boots")),
        _sec("Lv.2 - 青铜与金装备",
             E("bronze_sword", "b", 2), E("bronze_chestplate", "b", 2),
             E("gold_sword", "b", 2), E("gold_chestplate", "b", 2),
             E("gold_leggings", "b", 2), E("gold_boots", "b", 2)),
        _sec("Lv.3 - 铁制装备",
             E("iron_sword", "b", 3), E("iron_helmet", "b", 3),
             E("iron_chestplate", "b", 3), E("iron_leggings", "b", 3),
             E("iron_boots", "b", 3)),
        _sec("Lv.3 - 钢制装备",
             E("steel_sword", "b", 3), E("steel_helmet", "b", 3),
             E("steel_chestplate", "b", 3), E("steel_leggings", "b", 3),
             E("steel_boots", "b", 3)),
    ],
}

SHOPS["MiZhen"] = {
    "name": '<gold>米真', "currency": "point",
    "sections": [
        _sec("Lv.1 - 基础材料",
             E("coal"), E("charcoal"), E("flint"), E("matches"), E("tape"),
             E("flint_sharp")),
        _sec("Lv.1 - 旧物收购",
             E("rag", "s"), E("dust", "s"), E("bone", "s"), E("nail", "s"),
             E("screw", "s"), E("leather_unprocessed", "s")),
        _sec("Lv.2 - 锭材",
             E("copper_ingot", "b", 2), E("iron_ingot", "b", 2),
             E("bronze_ingot", "b", 2), E("brass_ingot", "b", 2),
             E("basic_alloy_ingot", "b", 2), E("steel_ingot", "b", 2),
             E("gold_ingot_medium", "b", 2), E("gold_ingot_high", "b", 2)),
    ],
}

SHOPS["XingChen"] = {
    "name": '<gold>星辰', "currency": "point",
    "sections": [
        _sec("Lv.1 - 贸易品",
             E("gold_ingot_medium"), E("copper_wire"), E("silver_wire")),
        _sec("Lv.1 - 矿石收购",
             E("raw_iron", "s"), E("raw_copper", "s"), E("raw_gold", "s"),
             E("coal", "s"), E("stone", "s")),
        _sec("Lv.2 - 贵金属",
             E("gold_ingot_high", "b", 2), E("golden_necklace", "b", 2, None, 1)),
        _sec("Lv.3 - 高端宝石收购",
             E("black_diamond", "s")),
    ],
}

SHOPS["YiChan"] = {
    "name": '<gold>一蝉', "currency": "point",
    "sections": [
        _sec("Lv.1 - 电子元件",
             E("copper_wire"), E("capacitor"), E("battery"), E("u_disk")),
        _sec("Lv.1 - 旧件收购",
             E("broken_disk", "s"), E("screw", "s"), E("nail", "s"),
             E("plastic", "s"), E("rag", "s")),
        _sec("Lv.2 - 核心元件",
             E("cpu", "b", 2), E("power_supply", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("pro_ai_core", "b", 0, "DutyLeave", 1)),
    ],
}

SHOPS["DaoGe"] = {
    "name": '<gold>刀哥', "currency": "point",
    "sections": [
        _sec("Lv.1 - 美食",
             E("barbecue"), E("cooked_porkchop"), E("cooked_beef"),
             E("cooked_chicken"), E("cooked_mutton")),
        _sec("Lv.1 - 掉落物收购",
             E("bone", "s"), E("hound_paw", "s"), E("spider_silk", "s"),
             E("scale", "s"), E("smilers_head", "s"), E("dullers_core", "s")),
        _sec("Lv.2 - 刀剑",
             E("bronze_sword", "b", 2), E("iron_sword", "b", 2),
             E("iron_chestplate", "b", 2), E("medium_rare_steak", "b", 2),
             E("taco", "b", 2)),
        _sec("Lv.3 - 钢制装备",
             E("steel_sword", "b", 3), E("steel_chestplate", "b", 3)),
        _sec("Lv.3 - 解锁商品",
             E("katana", "b", 0, "RunningToTheNight", 1)),
    ],
}

SHOPS["HuGe"] = {
    "name": '<gold>胡哥', "currency": "point",
    "sections": [
        _sec("Lv.1 - 口粮",
             E("bread"), E("cooked_porkchop"), E("cooked_beef"),
             E("cooked_chicken"), E("cooked_mutton"), E("apple_can"),
             E("beef_can")),
        _sec("Lv.1 - 食材收购",
             E("porkchop", "s"), E("beef", "s"), E("chicken", "s"),
             E("mutton", "s"), E("potato", "s"), E("carrot", "s"), E("wheat", "s")),
        _sec("Lv.2 - 军粮",
             E("iron_ingot", "b", 2), E("gold_ingot_medium", "b", 2)),
    ],
}

SHOPS["LiuHuo"] = {
    "name": '<gold>刘火', "currency": "point",
    "sections": [
        _sec("Lv.1 - 零件",
             E("copper_wire"), E("silver_wire"), E("iron_pickaxe")),
        _sec("Lv.1 - 旧件收购",
             E("nail", "s"), E("screw", "s"), E("gear", "s"), E("iron_ingot", "s"),
             E("plastic", "s")),
        _sec("Lv.2 - 工具与零件",
             E("precision_parts", "b", 2), E("motor", "b", 2), E("drill", "b", 2),
             E("wrench", "b", 2), E("hammer", "b", 2),
             E("engine_lubricating_oil", "b", 2), E("battery", "b", 2),
             E("battery_set", "b", 2), E("gold_ingot_low", "b", 2),
             E("gold_ingot_medium", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("advanced_ai_core", "b", 0, "PursuingPerformance", 1)),
    ],
}

SHOPS["Qisi"] = {
    "name": '<gold>七四', "currency": "point",
    "sections": [
        _sec("Lv.1 - 防疫医疗",
             E("sterile_bandage"), E("almond_bandage"), E("antibiotic")),
        _sec("Lv.1 - 材料收购",
             E("rag", "s"), E("glass_bottle", "s"), E("fabric_skin", "s")),
        _sec("Lv.2 - 进阶医疗",
             E("almond_water_blue", "b", 2), E("onefirst_medkit", "b", 2, None, 2)),
        _sec("Lv.3 - 解锁商品",
             E("akacandy_medkit", "b", 3, "WorkForFuture4", 1)),
    ],
}

SHOPS["ZhangShun"] = {
    "name": '<gold>张顺', "currency": "point",
    "sections": [
        _sec("Lv.1 - 旧物收购",
             E("paper", "s"), E("screw", "s"), E("battery", "s"),
             E("broken_disk", "s"), E("dust", "s"), E("rag", "s"),
             E("plastic", "s")),
        _sec("Lv.2 - 通信设备",
             E("radio", "b", 2, None, 2), E("basic_wifi_sender", "b", 2),
             E("advanced_wifi_sender", "b", 2), E("cpu", "b", 2),
             E("phone", "b", 2), E("gold_ingot_medium", "b", 2)),
        _sec("Lv.3 - 高级设备",
             E("wifi", "b", 3, None, 1)),
        _sec("Lv.3 - 解锁商品",
             E("advanced_ai_core", "b", 0, "Rocket", 1),
             E("golden_necklace", "b", 0, "FlashGoldCharge", 1)),
    ],
}

SHOPS["Greybird"] = {
    "name": '<gold>灰绑', "currency": "point",
    "sections": [
        _sec("Lv.1 - 基础矿物",
             E("iron_ingot"), E("copper_ingot"), E("silver_ingot"),
             E("gold_ingot_low")),
        _sec("Lv.2 - 中级珠宝",
             E("gold_ingot_medium", "b", 2), E("gold_ingot_high", "b", 2),
             E("synthetic_diamond", "b", 2), E("synthetic_ruby", "b", 2),
             E("synthetic_sapphire", "b", 2), E("synthetic_emerald", "b", 2)),
        _sec("Lv.3 - 高端回收（仅收购）",
             E("diamond", "s"), E("ruby", "s"), E("sapphire", "s"),
             E("emerald", "s"), E("black_diamond", "s"), E("mythril_ingot", "s"),
             E("ultra_alloy_ingot", "s"), E("rainbow_alloy_ingot", "s")),
        _sec("Lv.3 - 矿物收购",
             E("coal", "s")),
    ],
}

SHOPS["Quinn"] = {
    "name": '<gold>奎因', "currency": "point",
    "sections": [
        _sec("Lv.1 - 基础食品",
             E("bread"), E("cracker"), E("potato_chips"), E("chocolate"),
             E("muffin"), E("pie")),
        _sec("Lv.1 - 食材收购",
             E("wheat", "s"), E("carrot", "s"), E("potato", "s"), E("apple", "s")),
        _sec("Lv.2 - 精选食品",
             E("cola", "b", 2), E("mre", "b", 2), E("apple_can", "b", 2),
             E("cooked_porkchop", "b", 2), E("cooked_beef", "b", 2),
             E("cooked_chicken", "b", 2), E("pizza", "b", 2),
             E("almond_water_blue", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("royal_rations", "b", 0, "CherryBlossom", 1)),
    ],
}

SHOPS["Raven"] = {
    "name": '<gold>渡鸦', "currency": "point",
    "sections": [
        _sec("Lv.1 - 基础物资",
             E("bandage"), E("bread"), E("wood"), E("painkiller_pills"),
             E("almond_water_gray"), E("almond_water_red"),
             E("almond_water_green")),
        _sec("Lv.1 - 矿物收购",
             E("coal", "s"), E("iron_ingot", "s"), E("copper_ingot", "s"),
             E("stone", "s")),
        _sec("Lv.2 - 军需",
             E("mre", "b", 2), E("sterile_bandage", "b", 2),
             E("royal_rations", "b", 2, None, 1)),
        _sec("Lv.3 - 解锁商品",
             E("object_100", "b", 0, "SecretGoods", 1)),
    ],
}

SHOPS["LiuWei"] = {
    "name": '<gold>刘威', "currency": "money",
    "sections": [
        _sec("Lv.1 - 电站材料",
             E("wood"), E("stone"), E("coal"), E("raw_iron"), E("steel_ingot")),
        _sec("Lv.1 - 旧件收购",
             E("screw", "s"), E("nail", "s")),
        _sec("Lv.2 - 机电设备",
             E("motor", "b", 2), E("cpu", "b", 2), E("battery", "b", 2),
             E("copper_wire", "b", 2)),
        _sec("Lv.3 - 解锁商品",
             E("graphics_card", "b", 0, "Panopticoal4", 1)),
    ],
}

# 黑市：混合货币（point/tape/以物易物）+ 动态定价 + 礼包，格式特殊，单独输出
BLACK_MARKET = """BlackMarket:
  name: "<black>黑市"

  level:
    1:
      - type: level
        level: main
        value: 5
      - type: affection
        affection: BlackMarket
        value: 5
    2:
      - type: level
        level: main
        value: 15
      - type: affection
        affection: BlackMarket
        value: 10
      - type: quota
        balance: point
        value: 5000

  items:
    # ════════════════════════════════════════
    # 军火（贸易点）
    # ════════════════════════════════════════
    gunpowder_kangel:
      id: sertraline:gunpowder_kangel
      buy-requirements:
        - mode: level
          level: 2
          display: 需要商人等级 Lv.2
      buy:
        - currency: point
          amount: 60
      sell:
        - currency: point
          amount: 24
      limit:
        player: 2
        player-reset: 24h

    gunpowder_player:
      id: sertraline:gunpowder_player
      buy:
        - currency: point
          amount: 30
      sell:
        - currency: point
          amount: 12

    gunpowder_rain:
      id: sertraline:gunpowder_rain
      buy-requirements:
        - mode: level
          level: 2
          display: 需要商人等级 Lv.2
      buy:
        - currency: point
          amount: 60
      sell:
        - currency: point
          amount: 24
      limit:
        player: 2
        player-reset: 24h

    thermite:
      id: sertraline:thermite
      buy:
        - currency: point
          amount: 20
      sell:
        - currency: point
          amount: 8

    atomic_gunpowder:
      id: sertraline:atomic_gunpowder
      buy:
        - currency: point
          amount: 32
      sell:
        - currency: point
          amount: 13

    6ch_grenade:
      id: sertraline:6ch_grenade
      buy:
        - currency: point
          amount: 20
      sell:
        - currency: point
          amount: 8

    shakalaka_grenade:
      id: sertraline:shakalaka_grenade
      buy:
        - currency: point
          amount: 35
      sell:
        - currency: point
          amount: 14

    b_shakalaka_grenade:
      id: sertraline:b_shakalaka_grenade
      buy-requirements:
        - mode: level
          level: 2
          display: 需要商人等级 Lv.2
      buy:
        - currency: point
          amount: 75
      sell:
        - currency: point
          amount: 30
      limit:
        player: 1
        player-reset: 24h

    hqlm_petrol:
      id: sertraline:hqlm_petrol
      buy:
        - currency: point
          amount: 18
      sell:
        - currency: point
          amount: 7

    firesalt_petrol:
      id: sertraline:firesalt_petrol
      buy:
        - currency: point
          amount: 32
      sell:
        - currency: point
          amount: 13

    strong_firesalt_petrol:
      id: sertraline:strong_firesalt_petrol
      buy-requirements:
        - mode: level
          level: 2
          display: 需要商人等级 Lv.2
      buy:
        - currency: point
          amount: 70
      sell:
        - currency: point
          amount: 28
      limit:
        player: 2
        player-reset: 24h

    frontroad_grenade:
      id: sertraline:frontroad_grenade
      buy:
        - currency: point
          amount: 19
      sell:
        - currency: point
          amount: 8

    endroad_grenade:
      id: sertraline:endroad_grenade
      buy:
        - currency: point
          amount: 33
      sell:
        - currency: point
          amount: 13

    nizxc_flash:
      id: sertraline:nizxc_flash
      buy:
        - currency: point
          amount: 6
      sell:
        - currency: point
          amount: 2

    zx_stun:
      id: sertraline:zx_stun
      buy:
        - currency: point
          amount: 6
      sell:
        - currency: point
          amount: 2

    ice_stun:
      id: sertraline:ice_stun
      buy:
        - currency: point
          amount: 6
      sell:
        - currency: point
          amount: 2

    shengguang_grenade:
      id: sertraline:shengguang_grenade
      buy:
        - currency: point
          amount: 6
      sell:
        - currency: point
          amount: 2

    # ════════════════════════════════════════
    # 磁带货币商品（磁带币）
    # ════════════════════════════════════════
    red_tape:
      id: sertraline:red_tape
      buy:
        - currency: tape
          amount: 100

    blue_tape:
      id: sertraline:blue_tape
      buy:
        - currency: tape
          amount: 100

    # ════════════════════════════════════════
    # 以物易物：5 木头换 1 燧石
    # ════════════════════════════════════════
    flint:
      id: sertraline:flint
      buy:
        - item: "sertraline:wood"
          amount: 5
      sell:
        - item: "sertraline:wood"
          amount: 2

    # 多货币购买 + 动态定价
    cola:
      id: sertraline:cola
      buy:
        - currency: point
          amount: 14
          dynamic-pricing:
            max: 20
            min: 10

    # ════════════════════════════════════════
    # 旧物回收（仅收购，付现金）
    # ════════════════════════════════════════
    dust:
      id: sertraline:dust
      sell:
        - currency: money
          amount: 2

    trash_bag:
      id: sertraline:trash_bag
      sell:
        - currency: money
          amount: 3

    plastic:
      id: sertraline:plastic
      sell:
        - currency: money
          amount: 16

    # ════════════════════════════════════════
    # 礼包类商品
    # ════════════════════════════════════════

    # 2+1+1 随心配礼包
    two_one_one_pack:
      id: sertraline:diamond
      type: giftpack
      gift-pack: two_one_one
      buy:
        - currency: point
          amount: 149
"""


# ============================================================
# 输出
# ============================================================
def render_buy(currency, amount, gate, quest, limit):
    """返回 (requirements块, buy行, sell行) 文本。"""
    reqs = []
    if gate:
        reqs.append(f"      buy-requirements:\n"
                    f"        - mode: level\n"
                    f"          level: {gate}\n"
                    f"          display: 需要商人等级 Lv.{gate}")
    if quest:
        disp = QUEST_DISPLAY.get(quest, quest)
        if reqs:
            reqs.append(f"        - mode: quest\n"
                        f"          value: {quest}\n"
                        f"          display: 需要完成任务「{disp}」")
        else:
            reqs.append(f"      requirements:\n"
                        f"        - mode: quest\n"
                        f"          value: {quest}\n"
                        f"          display: \"需要完成任务「{disp}」\"")
    if limit:
        reqs.append(f"      limit:\n"
                    f"        player: {limit}\n"
                    f"        player-reset: 24h")
    return "\n".join(reqs)


def main():
    os.makedirs(SHOP_DIR, exist_ok=True)
    errors = []
    for shop_id, cfg in SHOPS.items():
        cur = cfg["currency"]
        out = [f"{shop_id}:", f"  name: \"{cfg['name']}\"", "  items:"]
        seen = set()
        for title, entries in cfg["sections"]:
            out.append(f"    # {title}")
            for (item, mode, gate, quest, limit) in entries:
                if item not in ALL_PRICE:
                    errors.append(f"[{shop_id}] 未知物品: {item}")
                    continue
                if item in seen:
                    errors.append(f"[{shop_id}] 重复物品: {item}")
                    continue
                seen.add(item)
                buy = ALL_PRICE[item]
                sell = round(buy * 0.4)
                if cur == "point":
                    buy_a, sell_a = round(buy / 10), round(sell / 10)
                    btxt, stxt = f"buy: point:{buy_a}", f"sell: point:{sell_a}"
                else:
                    btxt, stxt = f"buy: money:{buy}", f"sell: money:{sell}"
                req = render_buy(cur, buy, gate, quest, limit)
                out.append(f"    {item}:")
                out.append(f"      id: sertraline:{item}")
                if req:
                    out.append(req)
                if mode in ("b", "bs"):
                    out.append(f"      {btxt}")
                if mode in ("s", "bs"):
                    out.append(f"      {stxt}")
        path = os.path.join(SHOP_DIR, shop_id + ".yml")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(out) + "\n")
        print(f"已生成 {shop_id}.yml")
    with open(os.path.join(SHOP_DIR, "BlackMarket.yml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(BLACK_MARKET)
    print("已生成 BlackMarket.yml")
    if errors:
        print("\n".join(errors))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
