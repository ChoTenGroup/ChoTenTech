# -*- coding: utf-8 -*-
"""商店校验器：检查定价合规、防刷、防刷货币、禁售清单。

规则（与 shop_design.py 配套）：
- 任意物品：跨所有商店 min(buy) > max(sell)，point×10 折算 money（tape/以物易物跳过）
- 卖出价 = 买入价 40% ± 1 点（10 money）舍入容差
- 禁售：钥匙卡（tier PP/PPP）、bell/blackflag/minzepaper/rangefinder/level4chest_key*/doc_*/ceshi/test、
  无中文名异常物品
- 同基地上交任务物品不得 buy（允许 sell 收购）
- 钢以上材料/装备一律不得 buy
- 价格与 tier 表档位一致（装备按材质分档）
- 无重复物品键；YAML 可解析
"""
import io
import os
import re
import sys
import yaml

SHOP_DIR = r"E:\ChoTenTechbk\Bukkit\src\main\resources\shops"
TIER_FILE = r"C:\Users\Administrator\Desktop\3\f\itemoutputtiers.yml"
NAMES_FILE = r"C:\Users\Administrator\Desktop\3\f\itemoutputnames.yml"
P2M = 10  # 1 point = 10 money

BANDS = {"E": (10, 50), "D": (20, 90), "C": (100, 240), "B": (220, 480),
         "A": (500, 1800), "S": (1200, 4500), "P": (6000, 18000)}
EQUIP_BANDS = {"leather": (80, 150), "flint": (80, 150), "bronze": (1600, 2400),
               "gold": (2000, 2800), "iron": (6400, 9600),
               "basic_alloy": (8500, 13500), "steel": (12000, 18000)}
EQUIP_SUFFIX = ("helmet", "chestplate", "leggings", "boots", "sword", "pickaxe",
                "axe", "shovel", "hoe", "dagger", "knife", "mattock",
                "combataxe", "handaxe", "hammer")
EQUIP_MATS = ("leather", "flint", "bronze", "gold", "iron", "basic_alloy",
              "steel", "diamond", "ruby", "sapphire", "emerald",
              "black_diamond", "mythril", "ultra_alloy", "rainbow_alloy", "npa")
STEEL_PLUS = ("diamond", "ruby", "sapphire", "emerald", "black_diamond",
              "mythril", "ultra_alloy", "rainbow_alloy", "npa")

BASE_SHOPS = {
    "BNTG/Level_1": ["AkaRain", "HeDe", "Lemon", "MiZhen", "XingChen", "YiChan"],
    "BNTG/Level_2": ["DaoGe", "HuGe", "LiuHuo", "Qisi", "ZhangShun"],
    "BNTG/level_10": ["Greybird", "Quinn"],
    "BNTG/level_11": ["Raven"],
    "MEG/Level_1": ["Adon", "BlackSmith", "BoFeng", "Kael", "Lefty", "MaoSe",
                    "MinZe", "Rhodes", "Sylvia", "Vance", "Zero", "ZhiChen"],
    "MEG/Level_3": ["HaLei", "Ice", "JiShi", "Luka", "ZhongHai", "xite"],
    "MEG/Level_4": ["AkaCandy", "AmeRain", "TheAchu"],
    "MEG/Level_11": ["FangHuo", "QianXia"],
    "NonAligned/Level_11": ["LiuWei"],
}
SHOP_BASE = {s: b for b, ss in BASE_SHOPS.items() for s in ss}

BANNED_BUY = {
    "BNTG/Level_1": {"almond_water_gray", "cpufan", "disk", "gold_ingot_low",
                     "paper", "ram_stick", "silver_ingot", "vpx"},
    "BNTG/Level_2": {"almond_water_red", "bandage", "brick_plate", "fried_rice",
                     "fuel", "glass_material", "gold_ingot_high",
                     "gunpowder_kangel", "gunpowder_player", "gunpowder_rain",
                     "horse_sculpture", "intelligence_folder", "light_bulb",
                     "moonshine", "mre", "nail", "painkiller_pills",
                     "steel_ingot", "tape"},
    "BNTG/level_10": {"sql_pills_aqua", "vodka"},
    "BNTG/level_11": {"fabric_skin", "paper", "plastic", "storm_stone", "vodka"},
    "MEG/Level_1": {"almond_water_blue", "almond_water_gray", "almond_water_green",
                    "almond_water_red", "apple_can", "bandage", "barbecue",
                    "battery", "bean_can", "beef_can", "book",
                    "brazilian_barbecue", "bread", "capacitor", "chocolate",
                    "cola", "cola_nosugar", "copper_ingot", "copper_wire",
                    "corned_beef", "cpu", "cracker", "crisps",
                    "deathmoths_mucus", "dullers_core", "dullers_tissue",
                    "exotic_butters", "fabric_skin", "fish_can", "flat_stone",
                    "french_fries", "gold_ingot_high", "ice_tea", "iron_ingot",
                    "iron_pickaxe", "jam", "juda_chicken_chop", "kfc",
                    "luck_milk_banana", "mayonnaise", "mcdonald",
                    "medium_rare_steak", "milk", "mongolia_tea", "mre",
                    "muffin", "mushroom_can", "orange_can", "oreo", "paper",
                    "pie", "pineapple_can", "pizza", "popcorn", "potato_can",
                    "potato_chips", "power_supply", "precision_parts", "rag",
                    "silver_wire", "sprite", "sterile_bandage", "stone",
                    "sugar_cube", "sweetcorn_can", "taco", "thermite",
                    "tomato_can", "waffle", "wood"},
    "MEG/Level_3": {"almond_water_blue", "fuel", "jam", "juda_chicken_chop",
                    "potato", "vodka"},
    "MEG/Level_4": {"almond_water_gray", "basic_wifi_sender", "birthday_card",
                    "moonshine", "paper", "sanity_salt", "wifi"},
    "MEG/Level_11": {"cola", "cola_nosugar", "natto_flavored_cola", "u_disk"},
    "NonAligned/Level_11": {"birthday_card", "iron_ingot", "light_bulb"},
}

FORBIDDEN_RE = re.compile(
    r"^(keycard|key_card|bell|blackflag|minzepaper|rangefinder|"
    r"level4chest_key|doc_|ceshi|test|1hermes_madicinal|topaz|aaaaa|zulijian)")


def load_tiers():
    tiers = {}
    with io.open(TIER_FILE, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or ":" not in line:
                continue
            k, v = line.split(":", 1)
            tiers[k.strip()] = v.strip()
    return tiers


def load_names():
    names = {}
    with io.open(NAMES_FILE, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or ":" not in line:
                continue
            k, v = line.split(":", 1)
            names[k.strip()] = v.strip()
    return names


def is_equip(item):
    if not item.endswith(EQUIP_SUFFIX):
        return False
    mat = equip_material(item)
    return mat in EQUIP_MATS


def equip_material(item):
    if item.startswith("basic_alloy_") or item.startswith("black_diamond_") \
            or item.startswith("rainbow_alloy_"):
        return item.split("_")[0] + "_" + item.split("_")[1]
    return item.split("_", 1)[0]


def check_dup_keys(text, path, violations):
    root = yaml.compose(text)
    if root is None:
        return

    def walk(node, depth):
        if isinstance(node, yaml.MappingNode):
            seen = {}
            for k, v in node.value:
                key = k.value if isinstance(k, yaml.ScalarNode) else repr(k)
                if key in seen:
                    violations.append(f"[{path}] 重复键: {key} (第{seen[key]}行/第{k.start_mark.line + 1}行)")
                else:
                    seen[key] = k.start_mark.line + 1
                walk(v, depth + 1)
        elif isinstance(node, yaml.SequenceNode):
            for v in node.value:
                walk(v, depth + 1)

    walk(root, 0)


def parse_entries(shop_id, items, violations):
    """返回 {item: {'buy': money, 'sell': money, 'currency': set, 'barter': bool}}，
    buy/sell 以 money 计（point×10），tape/以物易物不计入。"""
    out = {}
    for key, node in items.items():
        if not isinstance(node, dict):
            continue
        item = str(key)
        rec = {"buy": None, "sell": None, "cur": set(), "barter": False,
               "dynamic": False}
        for field, tag in (("buy", "buy"), ("sell", "sell")):
            val = node.get(field)
            if val is None:
                continue
            entries = val if isinstance(val, list) else [val]
            for e in entries:
                if isinstance(e, str):
                    m = re.match(r"^(money|point):([\d.]+)$", e)
                    if not m:
                        violations.append(f"[{shop_id}] {item} 无法解析 {field}: {e!r}")
                        continue
                    cur, amt = m.group(1), float(m.group(2))
                elif isinstance(e, dict):
                    if "currency" in e:
                        cur, amt = e["currency"], float(e.get("amount", 0))
                        if "dynamic-pricing" in e:
                            rec["dynamic"] = True
                    elif "item" in e:
                        rec["barter"] = True
                        continue
                    else:
                        violations.append(f"[{shop_id}] {item} 未知条目: {e!r}")
                        continue
                else:
                    continue
                rec["cur"].add(cur)
                if cur == "money":
                    money = amt
                elif cur == "point":
                    money = amt * P2M
                else:
                    continue  # tape 等其它货币不计入
                if rec[tag] is None:
                    rec[tag] = money
                elif rec[tag] != money:
                    violations.append(f"[{shop_id}] {item} 同字段多价格: {rec[tag]} vs {money}")
        out[item] = rec
    return out


def main():
    files = sorted(f for f in os.listdir(SHOP_DIR)
                   if f.endswith(".yml") and f not in ("example.yml", "shop-defaults.yml"))
    if len(files) != 39:
        print(f"[!!] 文件数 {len(files)} != 39，请检查")
    tiers = load_tiers()
    names = load_names()
    violations = []
    infos = []
    all_items = {}  # item -> {'buys': [...], 'sells': [...], 'shop': {...}}
    shop_entries = {}

    for fn in files:
        path = os.path.join(SHOP_DIR, fn)
        shop_id = fn[:-4]
        with io.open(path, encoding="utf-8") as f:
            text = f.read()
        check_dup_keys(text, fn, violations)
        data = yaml.safe_load(text)
        if not isinstance(data, dict):
            violations.append(f"[{fn}] YAML 根节点非 map")
            continue
        if len(data) == 1 and "items" in next(iter(data.values())):
            data = next(iter(data.values()))
        if "items" not in data or not isinstance(data["items"], dict):
            violations.append(f"[{fn}] 缺少 items")
            continue
        entries = parse_entries(shop_id, data["items"], violations)
        shop_entries[shop_id] = entries
        base = SHOP_BASE.get(shop_id)
        banned = BANNED_BUY.get(base, set()) if base else set()

        for item, rec in entries.items():
            node = data["items"][item]
            if isinstance(node, dict) and node.get("type") == "giftpack":
                continue  # 礼包条目（虚拟物品）
            rec["base"] = base
            all_items.setdefault(item, {"buys": [], "sells": [], "shop": {}})
            agg = all_items[item]
            agg["shop"][shop_id] = rec
            if rec["buy"] is not None:
                agg["buys"].append(rec["buy"])
                # ---- 禁售物品 ----
                if FORBIDDEN_RE.search(item):
                    violations.append(f"[{fn}] 禁售物品带 buy: {item}")
                if base and item in banned:
                    violations.append(f"[{fn}] 同基地上交物品禁售(buy): {item} @ {base}")
                if item in STEEL_PLUS or item.startswith(tuple(m + "_" for m in STEEL_PLUS)):
                    violations.append(f"[{fn}] 钢以上材料禁售(buy): {item}")
                if is_equip(item):
                    mat = equip_material(item)
                    if mat in STEEL_PLUS or (mat + "_") not in EQUIP_BANDS and mat not in EQUIP_BANDS:
                        violations.append(f"[{fn}] 钢以上装备禁售(buy): {item}")
                # ---- 中文名 ----
                if item not in names or not names[item]:
                    violations.append(f"[{fn}] 无中文名物品: {item}")
            if rec["sell"] is not None:
                agg["sells"].append(rec["sell"])

    # ---- 跨店套利：min(buy) > max(sell) ----
    for item, agg in sorted(all_items.items()):
        if agg["buys"] and agg["sells"]:
            mb = min(agg["buys"])
            ms = max(agg["sells"])
            if mb <= ms:
                shops = ", ".join(f"{s}" for s, r in agg["shop"].items())
                violations.append(
                    f"[套利] {item}: min(buy)={mb} <= max(sell)={ms} ({shops})")

    # ---- 档位检查 + 40% 检查 + PP+ ----
    for shop_id, entries in shop_entries.items():
        for item, rec in entries.items():
            tier = tiers.get(item)
            if tier in ("PP", "PPP"):
                violations.append(f"[{shop_id}] PP+ 物品禁交易: {item}")
            if rec["buy"] is None:
                continue
            buy = rec["buy"]
            if is_equip(item):
                mat = equip_material(item)
                if mat in EQUIP_BANDS:
                    lo, hi = EQUIP_BANDS[mat]
                    if not (lo <= buy <= hi):
                        violations.append(
                            f"[{shop_id}] {item} 装备价 {buy} 超出 {mat} 档 {lo}~{hi}")
                elif not rec["barter"]:
                    violations.append(f"[{shop_id}] {item} 未知装备材质 {mat}")
            elif tier:
                if tier not in BANDS:
                    violations.append(f"[{shop_id}] {item} 未知 tier: {tier}")
                else:
                    lo, hi = BANDS[tier]
                    if not (lo <= buy <= hi):
                        violations.append(
                            f"[{shop_id}] {item} tier {tier} 价格 {buy} 超出档位 {lo}~{hi}")
            else:
                infos.append(f"[{shop_id}] {item} 不在 tier 表（跳过档位检查）")
            # 40% ± 1 点
            if rec["sell"] is not None:
                expected = round(buy * 0.4)
                if abs(rec["sell"] - expected) > 10:
                    violations.append(
                        f"[{shop_id}] {item} 收购价 {rec['sell']} != 40%({expected}) "
                        f"(买 {buy})")

    print("=" * 60)
    print("违规报告")
    print("=" * 60)
    if violations:
        for v in sorted(set(violations)):
            print("VIOLATION:", v)
        print(f"\n共 {len(set(violations))} 条违规")
        sys.exit(1)
    print("零违规")
    if infos:
        print(f"\n{len(infos)} 条提示:")
        for i in infos:
            print("INFO:", i)
    n_items = len(all_items)
    n_buy = sum(1 for a in all_items.values() if a["buys"])
    n_sell_only = sum(1 for a in all_items.values() if a["sells"] and not a["buys"])
    print(f"\n共 {n_items} 个交易物品，{n_buy} 个有出售价，{n_sell_only} 个纯收购")


if __name__ == "__main__":
    main()
