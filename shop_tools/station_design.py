# -*- coding: utf-8 -*-
"""生成全部制作站 YAML（E:\ChoTenTechbk\Bukkit\src\main\resources\stations\）
配方设计遵循防刷原则：材料最高回收等价价(money) + 货币成本(money 或 point×10) >= 产物最高回收等价价(money)
"""
import os, sys, io, yaml
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

SHOP_DIR = r'E:\ChoTenTechbk\Bukkit\src\main\resources\shops'
OUT_DIR = r'E:\ChoTenTechbk\Bukkit\src\main\resources\stations'
os.makedirs(OUT_DIR, exist_ok=True)

# ---------- 产物最高回收等价价（money 基准；point×10） ----------
# 从各商店 sell 汇总得出，跨店取最高；未列出 => 0（无回收，安全）
SELL = {
    # 材料/锭
    'coal':44,'charcoal':40,'flint':40,'matches':20,'tape':20,'lighter':18,
    'raw_iron':50,'raw_copper':44,'raw_gold':60,'raw_coal':40,
    'iron_ingot':24,'copper_ingot':50,'silver_ingot':60,'bronze_ingot':96,
    'brass_ingot':104,'billon_ingot':108,'lead_ingot':48,'gold_ingot_low':112,
    'gold_ingot_medium':140,'basic_alloy_ingot':88,'steel_ingot':160,
    'clay_ball':52,'carbon':56,'wood':12,'stone':10,'leather_unprocessed':20,
    'fabric_skin':60,'fibre':14,'plastic':20,'hard_plastic':64,'glass_bottle':20,
    'glass_material':18,'rag':20,'paper':20,'dust':2,'broken_disk':20,
    'screw':12,'nail':12,'gear':20,'copper_wire':192,'silver_wire':192,'capacitor':192,
    'battery':192,'battery_set':192,'u_disk':192,'disk':360,'motor':192,'drill':192,
    'phone':360,'pcb':360,'raspberry_pi':360,'ram_stick':1200,'vpx':10000,'radio':1200,
    'light_bulb':192,'cpufan':192,'power_bank':192,'simple_watch':360,
    'examination_paper':192,'basic_ai_core':192,'cpu':360,'power_supply':192,
    'basic_wifi_sender':192,'advanced_wifi_sender':192,'wifi':2400,'sound_card':1280,
    'hearing_aid':1280,'record_player':1320,'3d_vision_glasses':1200,'graphics_card':1280,
    'advanced_ai_core':1360,'pro_ai_core':2800,
    # 食物
    'bread':24,'cracker':20,'chocolate':52,'muffin':52,'oreo':52,'popcorn':50,
    'potato_chips':50,'pie':54,'sprite':54,'kola':104,'ice_tea':60,'twofacesoda':104,
    'rose':100,'soda':20,'barbecue':130,'cooked_porkchop':64,'cooked_beef':70,
    'cooked_chicken':64,'cooked_mutton':70,'apple_can':60,'beef_can':110,'mre':128,
    'emergency_water_pack':64,'oatmeal':20,'bread_cubes':22,'konjac':22,'scits':56,
    'pizza_soup':18,'jerky':60,'sugar_cane':46,'cocoa_beans':48,'wheat':44,'carrot':44,
    'potato':44,'porkchop':52,'beef':60,'chicken':50,'mutton':52,'brown_mushroom':52,
    'red_mushroom':52,'apple':50,'berry':12,'spider_leg':16,'bone':14,'hound_paw':20,
    'scale':20,'smilers_head':120,'dullers_core':320,'dullers_tissue':320,
    'deathmoths_mucus':18,'spider_silk':60,'bursters_shell':280,
    # 医疗
    'bandage':100,'sterile_bandage':104,'painkiller_pills':64,'almond_bandage':72,
    'ice_medkit':64,'empty_injector':56,'antibiotic':120,'tianlong_medkit':120,
    '6ch_vaseline':120,'adrenaline_injector':128,'flower_of_fire_injector':128,
    'zako_healer_injector':128,'insulin':380,'onefirst_medkit':600,'zaped':400,
    'epsilon_pills':360,'sql_pills_aqua':360,'sql_pills_purple':360,'etg_injector':360,
    'fast_stamina_injector':360,'fast_strength_injector':360,'transfer_stamina_injector':360,
    'mule_injector':360,'golden_apple_injector':720,'inocentia_injector':720,
    'rainbow_injector':3200,'almond_water_blue':120,'almond_water_red':120,
    'almond_water_green':120,'almond_water_gray':120,
    # 装备（可回收部分）
    'leather_helmet':36,'leather_chestplate':48,'leather_leggings':42,'leather_boots':32,
    'flint_sword':48,'flint_pickaxe':44,'flint_axe':44,'flint_sharp':120,
    'bronze_sword':800,'bronze_chestplate':960,'bronze_boots':640,
    # 军火（point×10）
    'gunpowder_kangel':240,'gunpowder_player':120,'gunpowder_rain':240,'thermite':80,
    'atomic_gunpowder':130,'6ch_grenade':80,'shakalaka_grenade':140,'b_shakalaka_grenade':300,
    'hqlm_petrol':70,'firesalt_petrol':130,'strong_firesalt_petrol':280,
    'frontroad_grenade':80,'endroad_grenade':130,'nizxc_flash':20,'zx_stun':20,
    'ice_stun':20,'shengguang_grenade':20,
    # 宝石
    'diamond':640,'ruby':560,'sapphire':560,'emerald':520,'black_diamond':720,
    'mythril_ingot':1000,'ultra_alloy_ingot':6000,'rainbow_alloy_ingot':4800,
}

# ---------- 任务解锁显示名（须与任务文件 meta.name 一致） ----------
QUEST_DISPLAY = {
    'HarderSelf':'打铁还需自身硬','ScrapMetal':'破铜烂铁','TakeOffPlane':'人在后室刚下飞机',
    'AdultChoice':'大人的选择','DutyLeave':'擅离职守','RunningToTheNight':'向夜晚奔去',
    'OldIceBBQ':'老冰烧烤','TrainTravel3':'列车旅行 - III','InternetTravel2':'因特耐游记 - II',
    'QingQingShi':'青青石','DontLaughChallenge1':'不要笑挑战 - I','PursuingPerformance':'追求绩效',
    'WorkForFuture4':'为了今后的工作 - IV','Rocket':'这都有火箭收哦','FlashGoldCharge':'闪金冲锋',
    'CherryBlossom':'樱花树下','SecretGoods':'秘密货物','Panopticoal4':'煤炭监狱 - IV',
    'ClawMachinePower':'192号娃娃机',
}

# ---------- 配方成本类型 ----------
CUR = lambda amt, cur='money': ('cur', cur, amt)   # 货币成本 ('cur', money|point, amt)
IT  = lambda item, count=1: ('item', item, count)  # sertraline 物品成本

# ---------- 站数据 ----------
# 每个站: (id, name, queue, extra_affection(required,slots) or None, [配方...])
# 配方: dict(key=..., out=物品id, cost=[...], time=秒, quest=任务ID或None)
STATIONS = {}

STATIONS['BlackSmith'] = dict(
    name='<gold>铁匠', queue=8, affection=(30, 4),
    recipes=[
        # 炼炭与冶炼
        dict(key='raw_coal', out='raw_coal', time=15,
             cost=[IT('wood',1), CUR(35)]),
        dict(key='coal', out='coal', time=25,
             cost=[IT('raw_coal',1), CUR(20)]),
        dict(key='iron_ingot', out='iron_ingot', time=50,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(15)]),
        dict(key='copper_ingot', out='copper_ingot', time=45,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(15)]),
        dict(key='gold_ingot_low', out='gold_ingot_low', time=60,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(25)]),
        dict(key='bronze_ingot', out='bronze_ingot', time=65,
             cost=[IT('copper_ingot',1), IT('raw_iron',1), IT('coal',1), CUR(30)]),
        dict(key='brass_ingot', out='brass_ingot', time=65,
             cost=[IT('copper_ingot',1), IT('raw_copper',1), IT('coal',1), CUR(30)]),
        dict(key='billon_ingot', out='billon_ingot', time=70,
             cost=[IT('copper_ingot',1), IT('silver_ingot',1), CUR(40)]),
        dict(key='basic_alloy_ingot', out='basic_alloy_ingot', time=80,
             cost=[IT('iron_ingot',1), IT('carbon',1), CUR(40)]),
        dict(key='steel_ingot', out='steel_ingot', time=100,
             cost=[IT('iron_ingot',1), IT('carbon',1), IT('coal',1), CUR(60)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(1000)], quest='HarderSelf'),
        dict(key='bronze_sword', out='bronze_sword', time=80,
             cost=[IT('bronze_ingot',3), CUR(550)]),
        dict(key='bronze_helmet', out='bronze_helmet', time=70,
             cost=[IT('bronze_ingot',2), CUR(300)]),
        dict(key='bronze_chestplate', out='bronze_chestplate', time=95,
             cost=[IT('bronze_ingot',4), CUR(600)]),
        dict(key='bronze_leggings', out='bronze_leggings', time=85,
             cost=[IT('bronze_ingot',3), CUR(450)]),
        dict(key='bronze_boots', out='bronze_boots', time=65,
             cost=[IT('bronze_ingot',2), CUR(470)]),
        dict(key='iron_pickaxe', out='iron_pickaxe', time=80,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(600)]),
        dict(key='iron_helmet', out='iron_helmet', time=75,
             cost=[IT('iron_ingot',2), CUR(500)]),
        dict(key='iron_chestplate', out='iron_chestplate', time=105,
             cost=[IT('iron_ingot',4), CUR(900)]),
        dict(key='iron_leggings', out='iron_leggings', time=95,
             cost=[IT('iron_ingot',3), CUR(700)]),
        dict(key='iron_boots', out='iron_boots', time=75,
             cost=[IT('iron_ingot',2), CUR(400)]),
        dict(key='basic_alloy_sword', out='basic_alloy_sword', time=130,
             cost=[IT('basic_alloy_ingot',3), CUR(1200)]),
        dict(key='basic_alloy_chestplate', out='basic_alloy_chestplate', time=150,
             cost=[IT('basic_alloy_ingot',4), CUR(1500)]),
        dict(key='steel_sword', out='steel_sword', time=160,
             cost=[IT('steel_ingot',3), CUR(1800)]),
        dict(key='steel_chestplate', out='steel_chestplate', time=180,
             cost=[IT('steel_ingot',4), CUR(2200)]),
        dict(key='leather_chestplate', out='leather_chestplate', time=40,
             cost=[IT('leather_unprocessed',3), CUR(80)]),
        dict(key='flint_sword', out='flint_sword', time=30,
             cost=[IT('flint',2), CUR(40)]),
    ])

STATIONS['Adon'] = dict(
    name='<gold>亚当', queue=8, affection=None,
    recipes=[
        dict(key='battery', out='battery', time=50,
             cost=[IT('copper_wire',1), IT('carbon',1), CUR(20)]),
        dict(key='motor', out='motor', time=55,
             cost=[IT('copper_wire',1), IT('screw',2), CUR(30)]),
        dict(key='pcb', out='pcb', time=70,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('screw',1), CUR(40)]),
        dict(key='disk', out='disk', time=80,
             cost=[IT('copper_wire',1), IT('plastic',2), IT('iron_ingot',1), CUR(110)]),
        dict(key='u_disk', out='u_disk', time=55,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(30)]),
        dict(key='power_bank', out='power_bank', time=60,
             cost=[IT('battery',1), IT('copper_wire',1), CUR(20)]),
        dict(key='cpu', out='cpu', time=90,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('iron_ingot',1), CUR(30)]),
        dict(key='simple_watch', out='simple_watch', time=85,
             cost=[IT('pcb',1), IT('copper_wire',1), IT('iron_ingot',1), CUR(30)]),
        dict(key='phone', out='phone', time=95,
             cost=[IT('pcb',1), IT('battery',1), CUR(40)]),
        dict(key='ram_stick', out='ram_stick', time=110,
             cost=[IT('pcb',1), IT('copper_wire',2), IT('plastic',1), CUR(450)]),
        dict(key='radio', out='radio', time=140,
             cost=[IT('pcb',2), IT('copper_wire',2), CUR(120)]),
        dict(key='graphics_card', out='graphics_card', time=200,
             cost=[IT('pcb',3), IT('copper_wire',3), IT('ram_stick',1), CUR(300)]),
        dict(key='pro_ai_core', out='pro_ai_core', time=360,
             cost=[IT('cpu',1), IT('graphics_card',1), IT('ram_stick',1),
                   IT('advanced_ai_core',1), CUR(500)], quest='TakeOffPlane'),
    ])

STATIONS['BoFeng'] = dict(
    name='<gold>波风', queue=8, affection=None,
    recipes=[
        dict(key='cooked_porkchop', out='cooked_porkchop', time=20,
             cost=[IT('porkchop',1), CUR(30)]),
        dict(key='cooked_beef', out='cooked_beef', time=22,
             cost=[IT('beef',1), CUR(30)]),
        dict(key='cooked_chicken', out='cooked_chicken', time=20,
             cost=[IT('chicken',1), CUR(30)]),
        dict(key='cooked_mutton', out='cooked_mutton', time=22,
             cost=[IT('mutton',1), CUR(30)]),
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='emergency_water_pack', out='emergency_water_pack', time=40,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(50)]),
        dict(key='jerky', out='jerky', time=35,
             cost=[IT('porkchop',1), CUR(30)]),
        dict(key='honey', out='honey', time=120,
             cost=[IT('berry',4), IT('sugar_cane',1), CUR(140)]),
    ])

STATIONS['Kael'] = dict(
    name='<gold>凯尔', queue=8, affection=None,
    recipes=[
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(40)]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(60)]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(50)]),
        dict(key='almond_bandage', out='almond_bandage', time=45,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(30)]),
        dict(key='ice_medkit', out='ice_medkit', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(50)]),
        dict(key='empty_injector', out='empty_injector', time=40,
             cost=[IT('glass_bottle',1), IT('iron_ingot',1), CUR(30)]),
        dict(key='antibiotic', out='antibiotic', time=70,
             cost=[IT('fabric_skin',1), IT('glass_bottle',1), IT('paper',1), CUR(60)]),
        dict(key='tianlong_medkit', out='tianlong_medkit', time=75,
             cost=[IT('fabric_skin',2), IT('glass_bottle',1), CUR(40)]),
        dict(key='onefirst_medkit', out='onefirst_medkit', time=130,
             cost=[IT('fabric_skin',2), IT('glass_bottle',1), IT('antibiotic',1), CUR(380)]),
    ])

STATIONS['MaoSe'] = dict(
    name='<gold>毛瑟', queue=8, affection=None,
    recipes=[
        dict(key='flint_sword', out='flint_sword', time=30,
             cost=[IT('flint',2)]),
        dict(key='flint_pickaxe', out='flint_pickaxe', time=30,
             cost=[IT('flint',2)]),
        dict(key='flint_axe', out='flint_axe', time=30,
             cost=[IT('flint',2)]),
        dict(key='leather_helmet', out='leather_helmet', time=30,
             cost=[IT('leather_unprocessed',2)]),
        dict(key='leather_chestplate', out='leather_chestplate', time=40,
             cost=[IT('leather_unprocessed',3)]),
        dict(key='leather_boots', out='leather_boots', time=30,
             cost=[IT('leather_unprocessed',2)]),
        dict(key='gold_sword', out='gold_sword', time=90,
             cost=[IT('gold_ingot_medium',2), CUR(300)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(700)]),
        dict(key='iron_chestplate', out='iron_chestplate', time=120,
             cost=[IT('iron_ingot',4), CUR(1200)]),
        dict(key='steel_sword', out='steel_sword', time=160,
             cost=[IT('steel_ingot',3), CUR(1600)]),
    ])

STATIONS['MinZe'] = dict(
    name='<gold>悯泽', queue=8, affection=None,
    recipes=[
        dict(key='examination_paper', out='examination_paper', time=40,
             cost=[IT('paper',3), CUR(140)]),
        dict(key='disk', out='disk', time=80,
             cost=[IT('copper_wire',1), IT('plastic',1), IT('iron_ingot',1), CUR(140)]),
        dict(key='u_disk', out='u_disk', time=55,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(30)]),
        dict(key='basic_ai_core', out='basic_ai_core', time=90,
             cost=[IT('copper_wire',2), IT('plastic',1), CUR(20)]),
        dict(key='ultraviolet_lamp', out='ultraviolet_lamp', time=100,
             cost=[IT('pcb',1), IT('light_bulb',1), IT('copper_wire',1), CUR(100)]),
        dict(key='soundproof_panel', out='soundproof_panel', time=100,
             cost=[IT('pcb',1), IT('plastic',2), CUR(100)]),
        dict(key='hearing_aid', out='hearing_aid', time=120,
             cost=[IT('pcb',1), IT('copper_wire',2), CUR(560)]),
        dict(key='record_player', out='record_player', time=150,
             cost=[IT('pcb',1), IT('motor',1), IT('copper_wire',1), CUR(600)]),
        dict(key='3d_vision_glasses', out='3d_vision_glasses', time=160,
             cost=[IT('pcb',1), IT('copper_wire',2), CUR(460)]),
        dict(key='pro_ai_core', out='pro_ai_core', time=360,
             cost=[IT('basic_ai_core',1), IT('cpu',1), IT('ram_stick',1), CUR(1500)],
             quest='TakeOffPlane'),
        dict(key='capacitor', out='capacitor', time=35,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(30)]),
        dict(key='battery', out='battery', time=50,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(50)]),
        dict(key='cpufan', out='cpufan', time=55,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(60)]),
        dict(key='motor', out='motor', time=60,
             cost=[IT('copper_wire',1), IT('screw',2), CUR(60)]),
        dict(key='cpu', out='cpu', time=90,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('iron_ingot',1), CUR(60)]),
        dict(key='ram_stick', out='ram_stick', time=120,
             cost=[IT('pcb',1), IT('copper_wire',2), IT('plastic',1), CUR(470)]),
        dict(key='vpx', out='vpx', time=300,
             cost=[IT('cpu',1), IT('ram_stick',1), IT('pcb',1), CUR(8100)]),
    ])

STATIONS['Sylvia'] = dict(
    name='<gold>西尔维亚', queue=8, affection=None,
    recipes=[
        dict(key='examination_paper', out='examination_paper', time=40,
             cost=[IT('paper',3), CUR(140)]),
        dict(key='simple_watch', out='simple_watch', time=85,
             cost=[IT('pcb',1), IT('copper_wire',1), IT('iron_ingot',1), CUR(30)]),
        dict(key='radio', out='radio', time=140,
             cost=[IT('pcb',2), IT('copper_wire',2), CUR(130)]),
        dict(key='advanced_wifi_sender', out='advanced_wifi_sender', time=120,
             cost=[IT('pcb',1), IT('copper_wire',2), CUR(80)]),
        dict(key='sound_card', out='sound_card', time=130,
             cost=[IT('pcb',1), IT('copper_wire',2), CUR(560)]),
        dict(key='record_player', out='record_player', time=150,
             cost=[IT('pcb',1), IT('motor',1), IT('copper_wire',1), CUR(600)]),
        dict(key='3d_vision_glasses', out='3d_vision_glasses', time=160,
             cost=[IT('pcb',1), IT('copper_wire',2), CUR(460)]),
        dict(key='wifi', out='wifi', time=200,
             cost=[IT('advanced_wifi_sender',1), IT('pcb',1), IT('copper_wire',2), CUR(1600)]),
    ])

STATIONS['Vance'] = dict(
    name='<gold>万斯', queue=8, affection=None,
    recipes=[
        dict(key='jerky', out='jerky', time=35,
             cost=[IT('porkchop',1), CUR(30)]),
        dict(key='emergency_water_pack', out='emergency_water_pack', time=40,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(50)]),
        dict(key='oatmeal', out='oatmeal', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='bread_cubes', out='bread_cubes', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='scits', out='scits', time=30,
             cost=[IT('wheat',1), CUR(20)]),
        dict(key='konjac', out='konjac', time=30,
             cost=[IT('brown_mushroom',1), CUR(10)]),
        dict(key='herring_can', out='herring_can', time=80,
             cost=[IT('berry',3), IT('wheat',1), CUR(150)]),
        dict(key='honey', out='honey', time=120,
             cost=[IT('berry',4), IT('sugar_cane',1), CUR(140)]),
        dict(key='royal_rations', out='royal_rations', time=240,
             cost=[IT('herring_can',1), IT('honey',1), IT('jerky',1),
                   IT('mre',1), CUR(600)], quest='AdultChoice'),
    ])

STATIONS['Zero'] = dict(
    name='<gold>零', queue=8, affection=None,
    recipes=[
        dict(key='vodka', out='vodka', time=120,
             cost=[IT('potato',2), CUR(120)]),
        dict(key='moonshine', out='moonshine', time=130,
             cost=[IT('wheat',2), IT('sugar_cane',1), CUR(150)]),
        dict(key='beer', out='beer', time=110,
             cost=[IT('wheat',2), CUR(100)]),
        dict(key='rio', out='rio', time=90,
             cost=[IT('berry',3), IT('sugar_cane',1), CUR(80)]),
        dict(key='suntory_whisky', out='suntory_whisky', time=180,
             cost=[IT('wheat',2), CUR(200)]),
        dict(key='welcome_wine', out='welcome_wine', time=100,
             cost=[IT('berry',2), CUR(100)]),
        dict(key='tno_noodles', out='tno_noodles', time=110,
             cost=[IT('wheat',2), IT('potato',1), CUR(120)]),
    ])

STATIONS['ZhiChen'] = dict(
    name='<gold>知陈', queue=8, affection=None,
    recipes=[
        dict(key='matches', out='matches', time=15,
             cost=[IT('wood',2), CUR(5)]),
        dict(key='tape', out='tape', time=15,
             cost=[IT('plastic',1), CUR(10)]),
        dict(key='lighter', out='lighter', time=20,
             cost=[IT('flint',1), CUR(10)]),
        dict(key='charcoal', out='charcoal', time=40,
             cost=[IT('wood',2), CUR(30)]),
        dict(key='flint_sharp', out='flint_sharp', time=25,
             cost=[IT('flint',2), IT('stone',1), CUR(40)]),
        dict(key='flint_sword', out='flint_sword', time=30,
             cost=[IT('flint',2), CUR(20)]),
        dict(key='flint_pickaxe', out='flint_pickaxe', time=30,
             cost=[IT('flint',2), CUR(20)]),
        dict(key='rope', out='rope', time=25,
             cost=[IT('berry',2), CUR(40)]),
        dict(key='leather_chestplate', out='leather_chestplate', time=40,
             cost=[IT('leather_unprocessed',3), CUR(20)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(700)]),
        dict(key='damascus_steel_ingot', out='damascus_steel_ingot', time=300,
             cost=[IT('steel_ingot',2), CUR(800)], quest='ScrapMetal'),
    ])

# ============ MEG Level_3 ============
STATIONS['HaLei'] = dict(
    name='<gold>哈雷', queue=8, affection=None,
    recipes=[
        # 炼炭与冶炼（材料与铁匠不同）
        dict(key='raw_coal', out='raw_coal', time=15,
             cost=[IT('wood',1), CUR(30)]),
        dict(key='coal', out='coal', time=25,
             cost=[IT('raw_coal',1), CUR(30)]),
        dict(key='iron_ingot', out='iron_ingot', time=50,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(20)]),
        dict(key='bronze_ingot', out='bronze_ingot', time=65,
             cost=[IT('copper_ingot',1), IT('iron_ingot',1), CUR(40)]),
        dict(key='gold_ingot_low', out='gold_ingot_low', time=60,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(30)]),
        dict(key='basic_alloy_ingot', out='basic_alloy_ingot', time=80,
             cost=[IT('iron_ingot',1), IT('bronze_ingot',1), CUR(50)]),
        dict(key='steel_ingot', out='steel_ingot', time=100,
             cost=[IT('iron_ingot',2), IT('carbon',1), CUR(80)]),
        dict(key='bronze_sword', out='bronze_sword', time=90,
             cost=[IT('bronze_ingot',3), CUR(600)]),
        dict(key='bronze_boots', out='bronze_boots', time=75,
             cost=[IT('bronze_ingot',2), CUR(500)]),
        dict(key='gold_sword', out='gold_sword', time=95,
             cost=[IT('gold_ingot_medium',2), CUR(400)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(700)]),
        dict(key='iron_chestplate', out='iron_chestplate', time=120,
             cost=[IT('iron_ingot',4), CUR(1200)]),
        dict(key='steel_sword', out='steel_sword', time=160,
             cost=[IT('steel_ingot',3), CUR(1600)]),
        dict(key='steel_chestplate', out='steel_chestplate', time=180,
             cost=[IT('steel_ingot',4), CUR(2000)]),
    ])

STATIONS['Ice'] = dict(
    name='<gold>冰块', queue=8, affection=None,
    recipes=[
        dict(key='barbecue', out='barbecue', time=60,
             cost=[IT('porkchop',1), IT('beef',1), CUR(40)]),
        dict(key='cooked_porkchop', out='cooked_porkchop', time=20,
             cost=[IT('porkchop',1), CUR(30)]),
        dict(key='cooked_beef', out='cooked_beef', time=22,
             cost=[IT('beef',1), CUR(30)]),
        dict(key='cooked_chicken', out='cooked_chicken', time=20,
             cost=[IT('chicken',1), CUR(30)]),
        dict(key='cooked_mutton', out='cooked_mutton', time=22,
             cost=[IT('mutton',1), CUR(30)]),
        dict(key='taco', out='taco', time=50,
             cost=[IT('porkchop',1), IT('wheat',1), CUR(60)]),
        dict(key='medium_rare_steak', out='medium_rare_steak', time=80,
             cost=[IT('beef',2), CUR(150)]),
        dict(key='brazilian_barbecue', out='brazilian_barbecue', time=130,
             cost=[IT('barbecue',1), IT('beef',1), CUR(200)], quest='OldIceBBQ'),
    ])

STATIONS['JiShi'] = dict(
    name='<gold>技师', queue=8, affection=None,
    recipes=[
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(40)]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(60)]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(50)]),
        dict(key='charcoal', out='charcoal', time=40,
             cost=[IT('wood',2), CUR(30)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(700)]),
        dict(key='iron_chestplate', out='iron_chestplate', time=120,
             cost=[IT('iron_ingot',4), CUR(1200)]),
        dict(key='steel_sword', out='steel_sword', time=160,
             cost=[IT('steel_ingot',3), CUR(1600)]),
        dict(key='onefirst_medkit', out='onefirst_medkit', time=130,
             cost=[IT('fabric_skin',2), IT('rag',2), CUR(460)]),
    ])

STATIONS['Luka'] = dict(
    name='<gold>卢卡', queue=8, affection=None,
    recipes=[
        dict(key='cracker', out='cracker', time=20,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='chocolate', out='chocolate', time=35,
             cost=[IT('cocoa_beans',1), IT('milk',1), CUR(20)]),
        dict(key='muffin', out='muffin', time=35,
             cost=[IT('wheat',1), IT('berry',1), CUR(20)]),
        dict(key='oreo', out='oreo', time=40,
             cost=[IT('cocoa_beans',1), IT('wheat',1), CUR(20)]),
        dict(key='popcorn', out='popcorn', time=30,
             cost=[IT('wheat',1), CUR(20)]),
        dict(key='potato_chips', out='potato_chips', time=30,
             cost=[IT('potato',1), CUR(20)]),
        dict(key='pie', out='pie', time=40,
             cost=[IT('wheat',1), IT('berry',1), CUR(20)]),
        dict(key='max_noodles', out='max_noodles', time=120,
             cost=[IT('wheat',2), IT('beef',1), CUR(120)]),
        dict(key='candy_can', out='candy_can', time=150,
             cost=[IT('sugar_cane',2), IT('cocoa_beans',2), CUR(900)]),
        dict(key='candy_stanleys', out='candy_stanleys', time=180,
             cost=[IT('sugar_cane',2), IT('cocoa_beans',2), CUR(1200)],
             quest='ClawMachinePower'),
    ])

STATIONS['ZhongHai'] = dict(
    name='<gold>钟海', queue=8, affection=None,
    recipes=[
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(40)]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(60)]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(50)]),
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='cracker', out='cracker', time=20,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='gold_ingot_medium', out='gold_ingot_medium', time=80,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(80)]),
        dict(key='steel_ingot', out='steel_ingot', time=110,
             cost=[IT('iron_ingot',1), IT('carbon',1), IT('coal',1), CUR(80)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(700)]),
        dict(key='iron_chestplate', out='iron_chestplate', time=120,
             cost=[IT('iron_ingot',4), CUR(1200)]),
    ])

STATIONS['xite'] = dict(
    name='<gold>西特', queue=8, affection=None,
    recipes=[
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(40)]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(60)]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(50)]),
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='cracker', out='cracker', time=20,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='tape', out='tape', time=15,
             cost=[IT('plastic',1), CUR(10)]),
        dict(key='almond_water_gray', out='almond_water_gray', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(90)]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(60)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(700)]),
        dict(key='steel_sword', out='steel_sword', time=160,
             cost=[IT('steel_ingot',3), CUR(1600)]),
    ])

# ============ MEG Level_4 ============
STATIONS['AkaCandy'] = dict(
    name='<gold>阿卡糖果', queue=8, affection=None,
    recipes=[
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='cracker', out='cracker', time=20,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='chocolate', out='chocolate', time=35,
             cost=[IT('cocoa_beans',1), IT('milk',1), CUR(20)]),
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(40)]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(60)]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(50)]),
        dict(key='antibiotic', out='antibiotic', time=70,
             cost=[IT('fabric_skin',1), IT('glass_bottle',1), IT('paper',1), CUR(60)]),
        dict(key='apple_can', out='apple_can', time=60,
             cost=[IT('apple',1), IT('sugar_cane',1), CUR(30)]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(60)]),
        dict(key='almond_water_blue', out='almond_water_blue', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(90)]),
    ])

STATIONS['AmeRain'] = dict(
    name='<gold>阿梅雨', queue=8, affection=None,
    recipes=[
        dict(key='copper_wire', out='copper_wire', time=30,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(120)]),
        dict(key='battery', out='battery', time=50,
             cost=[IT('copper_wire',1), IT('carbon',1), CUR(20)]),
        dict(key='pcb', out='pcb', time=70,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('screw',1), CUR(40)]),
        dict(key='cpu', out='cpu', time=90,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('iron_ingot',1), CUR(30)]),
        dict(key='phone', out='phone', time=95,
             cost=[IT('pcb',1), IT('battery',1), CUR(40)]),
        dict(key='advanced_wifi_sender', out='advanced_wifi_sender', time=120,
             cost=[IT('pcb',1), IT('copper_wire',2), CUR(80)]),
        dict(key='3d_vision_glasses', out='3d_vision_glasses', time=160,
             cost=[IT('pcb',1), IT('copper_wire',2), CUR(460)]),
        dict(key='graphics_card', out='graphics_card', time=200,
             cost=[IT('pcb',3), IT('copper_wire',3), IT('ram_stick',1), CUR(300)]),
        dict(key='advanced_ai_core', out='advanced_ai_core', time=300,
             cost=[IT('cpu',1), IT('ram_stick',1), IT('pcb',2), CUR(400)],
             quest='InternetTravel2'),
        dict(key='pro_ai_core', out='pro_ai_core', time=360,
             cost=[IT('advanced_ai_core',1), IT('graphics_card',1), CUR(500)]),
    ])

STATIONS['TheAchu'] = dict(
    name='<gold>阿初', queue=8, affection=None,
    recipes=[
        dict(key='almond_water_blue', out='almond_water_blue', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(90)]),
        dict(key='almond_water_red', out='almond_water_red', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(90)]),
        dict(key='almond_water_green', out='almond_water_green', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(90)]),
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(40)]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(60)]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(50)]),
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='apple_can', out='apple_can', time=60,
             cost=[IT('apple',1), IT('sugar_cane',1), CUR(30)]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(60)]),
        dict(key='boiled_almond_water', out='boiled_almond_water', time=120,
             cost=[IT('almond_water_blue',1), IT('coal',1), CUR(100)]),
    ])

# ============ MEG Level_11 ============
STATIONS['FangHuo'] = dict(
    name='<gold>房火', queue=8, affection=None,
    recipes=[
        dict(key='tape', out='tape', time=15,
             cost=[IT('plastic',1), CUR(10)]),
        dict(key='red_tape', out='red_tape', time=120,
             cost=[IT('tape',1), IT('fabric_skin',1), CUR(300)]),
        dict(key='blue_tape', out='blue_tape', time=120,
             cost=[IT('tape',1), IT('fabric_skin',1), CUR(300)]),
        dict(key='rainbow_board', out='rainbow_board', time=150,
             cost=[IT('wood',1), IT('tape',1), CUR(400)]),
        dict(key='green_board', out='green_board', time=150,
             cost=[IT('wood',1), IT('tape',1), CUR(400)]),
        dict(key='disk', out='disk', time=80,
             cost=[IT('copper_wire',1), IT('plastic',1), IT('iron_ingot',1), CUR(130)]),
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(10)]),
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(40)]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(60)]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(700)]),
        dict(key='advanced_ai_core', out='advanced_ai_core', time=300,
             cost=[IT('cpu',1), IT('ram_stick',1), IT('pcb',2), CUR(400)],
             quest='QingQingShi'),
    ])

STATIONS['QianXia'] = dict(
    name='<gold>千夏', queue=8, affection=None,
    recipes=[
        dict(key='soda', out='soda', time=25,
             cost=[IT('sugar_cane',1), CUR(10)]),
        dict(key='sprite', out='sprite', time=35,
             cost=[IT('sugar_cane',1), CUR(20)]),
        dict(key='kola', out='kola', time=45,
             cost=[IT('sugar_cane',1), IT('cocoa_beans',1), CUR(30)]),
        dict(key='ice_tea', out='ice_tea', time=40,
             cost=[IT('sugar_cane',1), IT('berry',1), CUR(30)]),
        dict(key='twofacesoda', out='twofacesoda', time=45,
             cost=[IT('sugar_cane',1), IT('cocoa_beans',1), CUR(30)]),
        dict(key='rose', out='rose', time=50,
             cost=[IT('berry',2), IT('sugar_cane',1), CUR(50)]),
        dict(key='iron_ingot', out='iron_ingot', time=70,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(20)]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(60)]),
        dict(key='onefirst_medkit', out='onefirst_medkit', time=130,
             cost=[IT('fabric_skin',2), IT('rag',2), CUR(460)]),
    ])

# ============ BNTG Level_1（point） ============
STATIONS['AkaRain'] = dict(
    name='<gold>阿卡雨', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(2,'point')]),
        dict(key='cracker', out='cracker', time=20,
             cost=[IT('wheat',1), CUR(2,'point')]),
        dict(key='chocolate', out='chocolate', time=35,
             cost=[IT('cocoa_beans',1), CUR(3,'point')]),
        dict(key='muffin', out='muffin', time=35,
             cost=[IT('wheat',1), IT('berry',1), CUR(3,'point')]),
        dict(key='cola', out='cola', time=50,
             cost=[IT('sugar_cane',1), IT('cocoa_beans',1), CUR(10,'point')]),
        dict(key='apple_can', out='apple_can', time=60,
             cost=[IT('apple',1), IT('sugar_cane',1), CUR(4,'point')]),
        dict(key='almond_water_blue', out='almond_water_blue', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(9,'point')]),
    ])

STATIONS['HeDe'] = dict(
    name='<gold>赫德', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(5,'point')]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(4,'point')]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(4,'point')]),
        dict(key='antibiotic', out='antibiotic', time=70,
             cost=[IT('fabric_skin',1), IT('glass_bottle',1), IT('paper',1), CUR(4,'point')]),
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(2,'point')]),
        dict(key='book', out='book', time=80,
             cost=[IT('paper',3), CUR(20,'point')]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(5,'point')]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(70,'point')]),
        dict(key='onefirst_medkit', out='onefirst_medkit', time=130,
             cost=[IT('fabric_skin',2), IT('rag',2), CUR(45,'point')]),
    ])

STATIONS['Lemon'] = dict(
    name='<gold>柠檬', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='leather_helmet', out='leather_helmet', time=30,
             cost=[IT('leather_unprocessed',2), CUR(1,'point')]),
        dict(key='leather_chestplate', out='leather_chestplate', time=40,
             cost=[IT('leather_unprocessed',3)]),
        dict(key='leather_leggings', out='leather_leggings', time=35,
             cost=[IT('leather_unprocessed',2), CUR(1,'point')]),
        dict(key='leather_boots', out='leather_boots', time=30,
             cost=[IT('leather_unprocessed',2)]),
        dict(key='gold_sword', out='gold_sword', time=95,
             cost=[IT('gold_ingot_medium',2), CUR(30,'point')]),
        dict(key='bronze_chestplate', out='bronze_chestplate', time=100,
             cost=[IT('bronze_ingot',4), CUR(70,'point')]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(70,'point')]),
        dict(key='iron_chestplate', out='iron_chestplate', time=120,
             cost=[IT('iron_ingot',4), CUR(120,'point')]),
        dict(key='basic_alloy_sword', out='basic_alloy_sword', time=130,
             cost=[IT('basic_alloy_ingot',3), CUR(120,'point')]),
        dict(key='steel_sword', out='steel_sword', time=160,
             cost=[IT('steel_ingot',3), CUR(150,'point')]),
        dict(key='steel_chestplate', out='steel_chestplate', time=180,
             cost=[IT('steel_ingot',4), CUR(180,'point')]),
    ])

STATIONS['MiZhen'] = dict(
    name='<gold>米真', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='matches', out='matches', time=15,
             cost=[IT('wood',2), CUR(2,'point')]),
        dict(key='tape', out='tape', time=15,
             cost=[IT('plastic',1), CUR(2,'point')]),
        dict(key='charcoal', out='charcoal', time=40,
             cost=[IT('wood',2), CUR(3,'point')]),
        dict(key='flint_sharp', out='flint_sharp', time=25,
             cost=[IT('flint',2), IT('stone',1), CUR(4,'point')]),
        dict(key='copper_ingot', out='copper_ingot', time=60,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(2,'point')]),
        dict(key='iron_ingot', out='iron_ingot', time=70,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(2,'point')]),
        dict(key='bronze_ingot', out='bronze_ingot', time=75,
             cost=[IT('raw_copper',1), IT('raw_iron',1), IT('coal',1), CUR(2,'point')]),
        dict(key='basic_alloy_ingot', out='basic_alloy_ingot', time=85,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(2,'point')]),
        dict(key='steel_ingot', out='steel_ingot', time=110,
             cost=[IT('iron_ingot',2), IT('coal',2), CUR(6,'point')]),
    ])

STATIONS['XingChen'] = dict(
    name='<gold>星辰', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='copper_wire', out='copper_wire', time=30,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(12,'point')]),
        dict(key='silver_wire', out='silver_wire', time=35,
             cost=[IT('silver_ingot',1), IT('coal',1), CUR(9,'point')]),
        dict(key='gold_ingot_low', out='gold_ingot_low', time=70,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(3,'point')]),
        dict(key='gold_ingot_medium', out='gold_ingot_medium', time=80,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(5,'point')]),
        dict(key='golden_necklace', out='golden_necklace', time=240,
             cost=[IT('gold_ingot_medium',3), CUR(120,'point')], quest='FlashGoldCharge'),
    ])

STATIONS['YiChan'] = dict(
    name='<gold>一蝉', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='copper_wire', out='copper_wire', time=30,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(12,'point')]),
        dict(key='capacitor', out='capacitor', time=35,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(2,'point')]),
        dict(key='battery', out='battery', time=50,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(2,'point')]),
        dict(key='u_disk', out='u_disk', time=55,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(2,'point')]),
        dict(key='cpu', out='cpu', time=90,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('iron_ingot',1), CUR(3,'point')]),
        dict(key='power_supply', out='power_supply', time=100,
             cost=[IT('copper_wire',2), IT('iron_ingot',1), CUR(3,'point')]),
        dict(key='pro_ai_core', out='pro_ai_core', time=360,
             cost=[IT('basic_ai_core',1), IT('cpu',1), CUR(250,'point')],
             quest='DutyLeave'),
    ])

# ============ BNTG Level_2（point） ============
STATIONS['DaoGe'] = dict(
    name='<gold>刀哥', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='cooked_porkchop', out='cooked_porkchop', time=20,
             cost=[IT('porkchop',1), CUR(2,'point')]),
        dict(key='cooked_beef', out='cooked_beef', time=22,
             cost=[IT('beef',1), CUR(2,'point')]),
        dict(key='cooked_chicken', out='cooked_chicken', time=20,
             cost=[IT('chicken',1), CUR(2,'point')]),
        dict(key='cooked_mutton', out='cooked_mutton', time=22,
             cost=[IT('mutton',1), CUR(2,'point')]),
        dict(key='barbecue', out='barbecue', time=60,
             cost=[IT('porkchop',1), IT('beef',1), CUR(3,'point')]),
        dict(key='bronze_sword', out='bronze_sword', time=90,
             cost=[IT('bronze_ingot',3), CUR(60,'point')]),
        dict(key='iron_sword', out='iron_sword', time=100,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(70,'point')]),
        dict(key='iron_chestplate', out='iron_chestplate', time=120,
             cost=[IT('iron_ingot',4), CUR(120,'point')]),
        dict(key='steel_sword', out='steel_sword', time=160,
             cost=[IT('steel_ingot',3), CUR(150,'point')]),
        dict(key='katana', out='katana', time=300,
             cost=[IT('iron_ingot',2), IT('wood',1), CUR(150,'point')],
             quest='RunningToTheNight'),
    ])

STATIONS['HuGe'] = dict(
    name='<gold>胡哥', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(2,'point')]),
        dict(key='apple_can', out='apple_can', time=60,
             cost=[IT('apple',1), IT('sugar_cane',1), CUR(2,'point')]),
        dict(key='beef_can', out='beef_can', time=65,
             cost=[IT('beef',1), IT('potato',1), CUR(2,'point')]),
        dict(key='cooked_porkchop', out='cooked_porkchop', time=20,
             cost=[IT('porkchop',1), CUR(2,'point')]),
        dict(key='cooked_beef', out='cooked_beef', time=22,
             cost=[IT('beef',1), CUR(2,'point')]),
        dict(key='cooked_chicken', out='cooked_chicken', time=20,
             cost=[IT('chicken',1), CUR(2,'point')]),
        dict(key='cooked_mutton', out='cooked_mutton', time=22,
             cost=[IT('mutton',1), CUR(2,'point')]),
        dict(key='iron_ingot', out='iron_ingot', time=70,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(2,'point')]),
        dict(key='gold_ingot_medium', out='gold_ingot_medium', time=80,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(5,'point')]),
    ])

STATIONS['LiuHuo'] = dict(
    name='<gold>刘火', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='copper_wire', out='copper_wire', time=30,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(12,'point')]),
        dict(key='silver_wire', out='silver_wire', time=35,
             cost=[IT('silver_ingot',1), IT('coal',1), CUR(10,'point')]),
        dict(key='battery', out='battery', time=50,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(2,'point')]),
        dict(key='motor', out='motor', time=55,
             cost=[IT('copper_wire',1), IT('screw',2), CUR(2,'point')]),
        dict(key='drill', out='drill', time=60,
             cost=[IT('copper_wire',1), IT('iron_ingot',1), CUR(2,'point')]),
        dict(key='wrench', out='wrench', time=55,
             cost=[IT('copper_wire',1), IT('iron_ingot',1), CUR(2,'point')]),
        dict(key='precision_parts', out='precision_parts', time=90,
             cost=[IT('copper_wire',2), IT('gear',2), CUR(4,'point')]),
        dict(key='battery_set', out='battery_set', time=80,
             cost=[IT('battery',1), IT('copper_wire',1), CUR(4,'point')]),
        dict(key='gold_ingot_medium', out='gold_ingot_medium', time=80,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(5,'point')]),
        dict(key='advanced_ai_core', out='advanced_ai_core', time=300,
             cost=[IT('cpu',1), IT('ram_stick',1), CUR(100,'point')],
             quest='PursuingPerformance'),
    ])

STATIONS['Qisi'] = dict(
    name='<gold>七四', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(4,'point')]),
        dict(key='almond_bandage', out='almond_bandage', time=45,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(2,'point')]),
        dict(key='antibiotic', out='antibiotic', time=70,
             cost=[IT('fabric_skin',1), IT('glass_bottle',1), IT('paper',1), CUR(4,'point')]),
        dict(key='almond_water_blue', out='almond_water_blue', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(9,'point')]),
        dict(key='onefirst_medkit', out='onefirst_medkit', time=130,
             cost=[IT('fabric_skin',2), IT('rag',2), CUR(45,'point')]),
        dict(key='akacandy_medkit', out='akacandy_medkit', time=300,
             cost=[IT('onefirst_medkit',1), IT('antibiotic',1), CUR(50,'point')],
             quest='WorkForFuture4'),
    ])

STATIONS['ZhangShun'] = dict(
    name='<gold>张顺', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='tape', out='tape', time=15,
             cost=[IT('plastic',1), CUR(2,'point')]),
        dict(key='battery', out='battery', time=50,
             cost=[IT('copper_wire',1), IT('plastic',1), CUR(2,'point')]),
        dict(key='cpu', out='cpu', time=90,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('iron_ingot',1), CUR(3,'point')]),
        dict(key='radio', out='radio', time=140,
             cost=[IT('pcb',2), IT('copper_wire',2), CUR(10,'point')]),
        dict(key='wifi', out='wifi', time=200,
             cost=[IT('radio',1), IT('copper_wire',2), CUR(82,'point')]),
        dict(key='golden_necklace', out='golden_necklace', time=240,
             cost=[IT('gold_ingot_medium',3), CUR(120,'point')], quest='FlashGoldCharge'),
        dict(key='advanced_ai_core', out='advanced_ai_core', time=300,
             cost=[IT('cpu',1), IT('ram_stick',1), CUR(100,'point')],
             quest='Rocket'),
    ])

# ============ BNTG Level_10（point） ============
STATIONS['Greybird'] = dict(
    name='<gold>灰绑', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='copper_ingot', out='copper_ingot', time=60,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(2,'point')]),
        dict(key='iron_ingot', out='iron_ingot', time=70,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(2,'point')]),
        dict(key='silver_ingot', out='silver_ingot', time=65,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(2,'point')]),
        dict(key='gold_ingot_low', out='gold_ingot_low', time=70,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(3,'point')]),
        dict(key='gold_ingot_medium', out='gold_ingot_medium', time=80,
             cost=[IT('raw_gold',1), IT('coal',1), CUR(5,'point')]),
        dict(key='gold_ingot_high', out='gold_ingot_high', time=120,
             cost=[IT('gold_ingot_medium',2), IT('coal',1), CUR(15,'point')]),
    ])

STATIONS['Quinn'] = dict(
    name='<gold>奎因', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(2,'point')]),
        dict(key='cracker', out='cracker', time=20,
             cost=[IT('wheat',1), CUR(2,'point')]),
        dict(key='potato_chips', out='potato_chips', time=30,
             cost=[IT('potato',1), CUR(2,'point')]),
        dict(key='chocolate', out='chocolate', time=35,
             cost=[IT('cocoa_beans',1), CUR(3,'point')]),
        dict(key='muffin', out='muffin', time=35,
             cost=[IT('wheat',1), IT('berry',1), CUR(3,'point')]),
        dict(key='pie', out='pie', time=40,
             cost=[IT('wheat',1), IT('berry',1), CUR(3,'point')]),
        dict(key='apple_can', out='apple_can', time=60,
             cost=[IT('apple',1), IT('sugar_cane',1), CUR(2,'point')]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(5,'point')]),
        dict(key='royal_rations', out='royal_rations', time=240,
             cost=[IT('herring_can',1), IT('honey',1), IT('jerky',1),
                   IT('mre',1), CUR(50,'point')], quest='CherryBlossom'),
    ])

# ============ BNTG Level_11（point） ============
STATIONS['Raven'] = dict(
    name='<gold>渡鸦', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='bandage', out='bandage', time=40,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(4,'point')]),
        dict(key='sterile_bandage', out='sterile_bandage', time=50,
             cost=[IT('fabric_skin',1), IT('rag',1), CUR(4,'point')]),
        dict(key='painkiller_pills', out='painkiller_pills', time=45,
             cost=[IT('rag',1), IT('paper',1), CUR(4,'point')]),
        dict(key='bread', out='bread', time=25,
             cost=[IT('wheat',1), CUR(2,'point')]),
        dict(key='almond_water_gray', out='almond_water_gray', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(9,'point')]),
        dict(key='almond_water_red', out='almond_water_red', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(9,'point')]),
        dict(key='almond_water_green', out='almond_water_green', time=50,
             cost=[IT('glass_bottle',1), IT('rag',1), CUR(9,'point')]),
        dict(key='mre', out='mre', time=90,
             cost=[IT('wheat',1), IT('beef',1), CUR(5,'point')]),
        dict(key='royal_rations', out='royal_rations', time=240,
             cost=[IT('herring_can',1), IT('honey',1), IT('jerky',1),
                   IT('mre',1), CUR(50,'point')], quest='SecretGoods'),
    ])

# ============ NonAligned Level_11 ============
STATIONS['LiuWei'] = dict(
    name='<gold>刘威', queue=8, affection=None,
    recipes=[
        dict(key='copper_wire', out='copper_wire', time=30,
             cost=[IT('raw_copper',1), IT('coal',1), CUR(150)]),
        dict(key='battery', out='battery', time=50,
             cost=[IT('copper_wire',1), IT('carbon',1), CUR(20)]),
        dict(key='motor', out='motor', time=55,
             cost=[IT('copper_wire',1), IT('screw',2), CUR(30)]),
        dict(key='iron_ingot', out='iron_ingot', time=70,
             cost=[IT('raw_iron',1), IT('coal',1), CUR(20)]),
        dict(key='steel_ingot', out='steel_ingot', time=110,
             cost=[IT('iron_ingot',1), IT('carbon',1), IT('coal',1), CUR(80)]),
        dict(key='cpu', out='cpu', time=90,
             cost=[IT('copper_wire',2), IT('plastic',1), IT('iron_ingot',1), CUR(30)]),
        dict(key='power_supply', out='power_supply', time=100,
             cost=[IT('copper_wire',2), IT('iron_ingot',1), CUR(60)]),
        dict(key='graphics_card', out='graphics_card', time=200,
             cost=[IT('pcb',3), IT('copper_wire',3), IT('ram_stick',1), CUR(300)],
             quest='Panopticoal4'),
    ])

# ============ 黑市（point 军火） ============
STATIONS['BlackMarket'] = dict(
    name='<black>黑市', queue=8, affection=None, cur='point',
    recipes=[
        dict(key='thermite', out='thermite', time=40,
             cost=[IT('iron_ingot',1), IT('coal',1), CUR(3,'point')]),
        dict(key='atomic_gunpowder', out='atomic_gunpowder', time=50,
             cost=[IT('coal',1), IT('carbon',1), CUR(6,'point')]),
        dict(key='gunpowder_kangel', out='gunpowder_kangel', time=90,
             cost=[IT('coal',1), IT('carbon',1), CUR(15,'point')]),
        dict(key='gunpowder_rain', out='gunpowder_rain', time=90,
             cost=[IT('coal',1), IT('carbon',1), CUR(15,'point')]),
        dict(key='6ch_grenade', out='6ch_grenade', time=60,
             cost=[IT('iron_ingot',1), IT('coal',1), CUR(3,'point')]),
        dict(key='shakalaka_grenade', out='shakalaka_grenade', time=80,
             cost=[IT('iron_ingot',1), IT('coal',2), CUR(5,'point')]),
        dict(key='hqlm_petrol', out='hqlm_petrol', time=45,
             cost=[IT('coal',1), IT('carbon',1), CUR(2,'point')]),
        dict(key='firesalt_petrol', out='firesalt_petrol', time=60,
             cost=[IT('coal',2), IT('carbon',1), CUR(2,'point')]),
        dict(key='nizxc_flash', out='nizxc_flash', time=40,
             cost=[IT('paper',1), IT('coal',1), CUR(1,'point')]),
        dict(key='zx_stun', out='zx_stun', time=40,
             cost=[IT('paper',1), IT('coal',1), CUR(1,'point')]),
        dict(key='ice_stun', out='ice_stun', time=40,
             cost=[IT('paper',1), IT('coal',1), CUR(1,'point')]),
        dict(key='shengguang_grenade', out='shengguang_grenade', time=40,
             cost=[IT('paper',1), IT('coal',1), CUR(1,'point')]),
    ])

# ============ 生成 YAML ============
def build_buy(cost):
    """cost -> YAML buy list"""
    lines = []
    for c in cost:
        if c[0] == 'item':
            _, item, count = c
            lines.append('sertraline:%s:%d' % (item, count))
        elif c[0] == 'cur':
            _, cur, amt = c
            lines.append('%s:%d' % (cur, amt))
    return lines

def item_cost_value(cost, cur):
    """材料/货币成本的 money 等价总值（1 point = 0.1 money）"""
    total = 0.0
    for c in cost:
        if c[0] == 'item':
            _, item, count = c
            total += SELL.get(item, 0) * count
        elif c[0] == 'cur':
            _, cur, amt = c
            total += amt * (10 if cur == 'point' else 1)
    return total

def gen_yaml(sid, cfg):
    out = ['%s:' % sid,
           '  name: "%s"' % cfg['name'],
           '  queue: %d' % cfg.get('queue', 8)]
    aff = cfg.get('affection')
    if aff:
        req, slots = aff
        out += ['  extra-queue:',
                '    affection_1:',
                '      display: "信任达到 %d 点"' % req,
                '      affection:',
                '        id: "%s"' % sid,
                '        required: %d' % req,
                '      slots: %d' % slots]
    out.append('  recipes:')
    for r in cfg['recipes']:
        out.append('    %s:' % r['key'])
        out.append('      id: sertraline:%s' % r['out'])
        buy = build_buy(r['cost'])
        out.append('      buy:')
        for b in buy:
            out.append('        - %s' % b)
        out.append('      time: %d' % r['time'])
        if r.get('quest'):
            q = r['quest']
            disp = QUEST_DISPLAY.get(q, q)
            out.append('      condition:')
            out.append('        kether: "quest select %s quest completed"' % q)
            out.append('        display: |-')
            out.append('          完成 %s 任务' % disp)
    return '\n'.join(out) + '\n'

def verify():
    print('====== 防刷校验 ======')
    bad = []
    for sid, cfg in STATIONS.items():
        cur = cfg.get('cur', 'money')
        for r in cfg['recipes']:
            out = r['out']
            sell = SELL.get(out, 0)
            cost = item_cost_value(r['cost'], cur)
            if sell > cost:
                bad.append('%s.%s  产物 %s 回收价 %d > 成本 %d' % (sid, r['key'], out, sell, cost))
    if bad:
        print('\n'.join(bad))
    else:
        print('全部配方通过：产物最高回收价 <= 材料+货币成本')
    return len(bad)

if __name__ == '__main__':
    n_bad = verify()
    for sid, cfg in STATIONS.items():
        path = os.path.join(OUT_DIR, sid + '.yml')
        with open(path, 'w', encoding='utf-8') as f:
            f.write(gen_yaml(sid, cfg))
        print('生成 %s' % path)
    print('共 %d 个站' % len(STATIONS))
    sys.exit(1 if n_bad else 0)
