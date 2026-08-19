# -*- coding: utf-8 -*-
"""为 _temp/mobs 下实体配置 Sertraline 掉落物（参考 Dullers1 格式）。"""
import io
import os
import re

MOBS = r"D:\Chemdah\_temp\mobs"

# 掉落物表：实体 -> 掉落行列表。概率：1=必掉；0.5=50%；数量 1-2 表示范围。
DROP_MAP = {
    # ---- 钝人（WITHER_SKELETON）：钝人组织、钝人核心、骨头 ----
    "Dullers1": ["dullers_tissue 1-2 0.5", "bone 1 0.3", "dullers_core 1 0.05", "ruby 1 0.01"],
    "Dullers2": ["dullers_tissue 1-3 0.6", "bone 1-2 0.4", "dullers_core 1 0.08", "emerald 1 0.01"],
    "Dullers3": ["dullers_tissue 2-3 0.7", "bone 1-2 0.5", "dullers_core 1 0.12", "sapphire 1 0.01"],
    "Dullers4": ["dullers_tissue 2-4 0.8", "bone 2-3 0.6", "dullers_core 1 0.2", "diamond 1 0.01"],
    "Dullers5": ["dullers_tissue 3-5 0.9", "bone 2-4 0.7", "dullers_core 1-2 0.3", "black_diamond 1 0.005"],
    # ---- 肢团（SPIDER）：实体掉落 + 8种材料，阶位越高材料越稀有（最高A级）----
    "Clump1": ["spider_leg 1-2 0.5", "spider_silk 1 0.3", "bone 1 0.4",
               "wood 1-2 0.5", "stone 1-2 0.4", "plank 1 0.4", "rag 1 0.3", "coal 1 0.3", "clay_ball 1 0.25", "raw_copper 1 0.1", "raw_iron 1 0.08",
               "emerald 1 0.001"],
    "Clump2": ["spider_leg 1-3 0.6", "spider_silk 1-2 0.4", "bone 1 0.5",
               "wood 1-2 0.5", "stone 1-2 0.4", "copper_ingot 1 0.2", "iron_ingot 1 0.15", "leather_unprocessed 1 0.2", "copper_wire 1 0.15", "raw_gold 1 0.1", "silver_ingot 1 0.05",
               "sapphire 1 0.001"],
    "Clump3": ["spider_leg 2-3 0.7", "spider_silk 1-2 0.5", "bone 1-2 0.6",
               "copper_ingot 1-2 0.3", "iron_ingot 1-2 0.25", "gold_ingot_medium 1 0.15", "silver_ingot 1 0.15", "bronze_ingot 1 0.12", "basic_alloy_ingot 1 0.08", "steel_ingot 1 0.05", "gold_ingot_high 1 0.03",
               "ruby 1 0.001"],
    "Clump4": ["spider_leg 2-4 0.8", "spider_silk 1-2 0.6", "bone 2 0.7",
               "steel_ingot 1 0.15", "gold_ingot_high 1 0.1", "precision_parts 1 0.1", "cpu 1 0.08", "damascus_steel_ingot 1 0.05", "gold_silver_ingot 1 0.04", "synthetic_ruby 1 0.02", "mythril_ingot 1 0.02",
               "black_diamond 1 0.001"],
    # ---- 死亡飞蛾（BEE）：粘液、鳞片（保留原有 mmoitems 掉落）----
    "Deathmoths1": ["deathmoths_mucus 1 0.4", "scale 1 0.5", "ruby 1 0.005"],
    "Deathmoths2": ["deathmoths_mucus 1-2 0.5", "scale 1 0.6", "emerald 1 0.005"],
    "Deathmoths3": ["deathmoths_mucus 1-2 0.6", "scale 1-2 0.7", "sapphire 1 0.01"],
    "Deathmoths4": ["deathmoths_mucus 2-3 0.8", "scale 2-3 0.8", "diamond 1 0.005"],
    "DeathmothsF1": ["deathmoths_mucus 1 0.3", "scale 1 0.4", "ruby 1 0.005"],
    # ---- 尸鼠（SILVERFISH）：骨头、破布、皮革 ----
    "DeathRats": ["bone 1-2 0.5", "rag 1 0.3", "leather_unprocessed 1 0.2", "diamond 1 0.005"],
    # ---- 无面灵（HUSK）：木材、石材、木板、平坦石材、煤、骨头、破布、皮革、铜/铁/金锭 ----
    "Facelings1": ["wood 1 0.5", "stone 1 0.3", "plank 1 0.3", "flat_stone 1 0.3", "coal 1 0.2", "bone 1 0.4", "rag 1 0.3", "leather_unprocessed 1 0.2", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_low 1 0.002", "emerald 1 0.001"],
    "Facelings2": ["wood 1-2 0.6", "stone 1 0.4", "plank 1-2 0.4", "flat_stone 1 0.4", "coal 1 0.3", "bone 1-2 0.5", "rag 1 0.4", "leather_unprocessed 1 0.3", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_medium 1 0.002", "sapphire 1 0.001"],
    "Facelings3": ["wood 2-3 0.7", "stone 1-2 0.5", "plank 1-2 0.5", "flat_stone 1-2 0.5", "coal 1-2 0.4", "bone 1-2 0.6", "rag 1-2 0.5", "leather_unprocessed 1-2 0.4", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_high 1 0.002", "black_diamond 1 0.001"],
    # ---- 窃皮者（HUSK）：窃皮者之皮、皮革、骨头 ----
    "SkinStealer": ["skin_stealer_skin 1 0.6", "leather_unprocessed 1-2 0.5", "bone 1 0.3", "ruby 1 0.02"],
    # ---- 笑魇（ZOMBIE/SKELETON）：笑魇骨头、头颅、暗黑物质 ----
    "Smiler1": ["smilers_bone 1 0.5", "black 1-2 0.5", "smilers_head 1 0.05", "ruby 1 0.01"],
    "Smiler2": ["smilers_bone 1-2 0.6", "black 2-3 0.8", "smilers_head 1 0.08", "emerald 1 0.01"],
    "Smiler3": ["smilers_bone 1-2 0.7", "black 2-4 0.8", "smilers_head 1 0.1", "sapphire 1 0.01"],
    "Smiler4_1": ["smilers_bone 2-3 0.8", "black 2-5 0.8", "smilers_head 1 0.15", "diamond 1 0.01"],  # 疯狂的笑魇
    "Smiler5": ["smilers_bone 2-4 0.9", "black 2-6 0.8", "smilers_head 1-2 0.2", "black_diamond 1 0.005"],  # 错乱的笑魇
    # ---- 悲尸（HUSK）：木材、石材、骨头、破布、纸、铜/铁/金锭 ----
    "Wretches1": ["wood 1 0.5", "stone 1 0.3", "bone 1 0.2", "rag 1 0.3", "paper 1 0.2", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_low 1 0.002", "emerald 1 0.001"],
    "Wretches2": ["wood 1-2 0.6", "stone 1 0.4", "bone 1 0.3", "rag 1 0.4", "paper 1 0.3", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_low 1 0.002", "sapphire 1 0.001"],
    "Wretches3": ["wood 1-2 0.7", "stone 1-2 0.5", "bone 1 0.4", "rag 1-2 0.5", "paper 1 0.4", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_medium 1 0.002", "ruby 1 0.001"],
    "Wretches4": ["wood 2-3 0.8", "stone 1-2 0.6", "bone 1-2 0.5", "rag 1-2 0.6", "paper 1-2 0.5", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_medium 1 0.002", "diamond 1 0.001"],
    "Wretches5": ["wood 2-4 0.9", "stone 2-3 0.7", "bone 1-2 0.6", "rag 2-3 0.7", "paper 1-2 0.6", "copper_ingot 1 0.005", "iron_ingot 1 0.005", "gold_ingot_high 1 0.002", "black_diamond 1 0.001"],
    # ---- 原版生物 ----
    "CAVE_SPIDER": ["spider_silk 1 0.4", "spider_leg 1 0.3"],
    "WITHER_SKELETON": ["bone 1-2 0.5", "coal 1 0.3"],
    "GIANT": ["bone 2-3 0.5", "leather_unprocessed 1-2 0.3"],
}

# 保留旧 Drops（mmoitems 掉落系统）的实体
KEEP_DROPS = {"Deathmoths1", "Deathmoths2", "Deathmoths3", "Deathmoths4", "DeathmothsF1"}

HEADER = re.compile(r"^([A-Za-z0-9_]+):\s*$")
KEY2 = re.compile(r"^  ([A-Za-z0-9_]+):\s*$")


def sub_block_end(lines, i):
    """从键行 i 开始，返回其子块结束后的下标（含子块行）。"""
    j = i + 1
    while j < len(lines):
        s = lines[j]
        if not s.strip():
            j += 1
            continue
        if s.startswith("  - ") or s.startswith("    "):
            j += 1
            continue
        break
    return j


def transform(path):
    raw = io.open(path, encoding="utf-8").read()
    lines = raw.split("\n")
    # 按顶层键切块
    blocks = []
    cur = []
    for ln in lines:
        if HEADER.match(ln):
            if cur:
                blocks.append(cur)
            cur = [ln]
        else:
            cur.append(ln)
    if cur:
        blocks.append(cur)

    out_blocks = []
    changed = []
    for blk in blocks:
        name = HEADER.match(blk[0]).group(1)
        # 统计该名字在此文件中已出现的次数（含本块）
        seen = 0
        for ob in out_blocks:
            m = HEADER.match(ob[0])
            if m and m.group(1) == name:
                seen += 1
        key = name if name not in ("Smiler4",) else f"{name}_{seen+1}"
        if key not in DROP_MAP:
            # 未配置的实体保留原样
            out_blocks.append(blk)
            continue

        drops = DROP_MAP[key]
        body = blk[1:]
        # 移除旧 Sertraline 块
        new_body = []
        i = 0
        while i < len(body):
            s = body[i]
            m = KEY2.match(s)
            if m and m.group(1) == "Sertraline":
                i = sub_block_end(body, i)
                continue
            if m and m.group(1) == "Drops" and name not in KEEP_DROPS:
                i = sub_block_end(body, i)
                continue
            new_body.append(s)
            i += 1
        body = new_body

        # 构造 Sertraline 块
        sert = ["  Sertraline:", "    drops:"]
        for d in drops:
            sert.append(f"      - {d}")

        # 插入点：Faction 行后；若无则 Damage 行后
        ins = -1
        for i, s in enumerate(body):
            if s.startswith("  Faction:"):
                ins = i
                break
        if ins == -1:
            for i, s in enumerate(body):
                if s.startswith("  Damage:"):
                    ins = i
                    break
        if ins == -1:
            ins = 0
        body = body[: ins + 1] + sert + body[ins + 1 :]
        out_blocks.append([blk[0]] + body)
        changed.append(key)

    new_text = "\n".join("\n".join(b) for b in out_blocks)
    io.open(path, "w", encoding="utf-8", newline="").write(new_text)
    return changed


for f in sorted(os.listdir(MOBS)):
    if not f.endswith(".yml"):
        continue
    p = os.path.join(MOBS, f)
    c = transform(p)
    print(f"{f}: {c}")
