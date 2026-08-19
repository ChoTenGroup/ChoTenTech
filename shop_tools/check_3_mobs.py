# -*- coding: utf-8 -*-
"""打印三个实体的 Sertraline 掉落部分用于核对。"""
import io
import os
import re

MOBS = r"D:\Chemdah\_temp\mobs"
items = set()
for line in io.open(r"D:\Chemdah\itemoutputnames.yml", encoding="utf-8"):
    k = line.split(":", 1)[0].strip()
    if k:
        items.add(k)

bad = []
for f in ["Clump.yml", "Facelings.yml", "Wretches.yml"]:
    txt = io.open(os.path.join(MOBS, f), encoding="utf-8").read()
    print(f"=== {f} ===")
    cur = None
    for ln in txt.split("\n"):
        m = re.match(r"^([A-Za-z0-9_]+):\s*$", ln)
        if m:
            cur = m.group(1)
        if re.match(r"^\s+- ", ln) or ln.strip() == "drops:":
            print(f"  {cur}: {ln.strip()}")
            if ln.strip() != "drops:":
                mm = re.match(r"^- (\S+) (\S+) ([0-9.]+)$", ln.strip())
                if mm:
                    iid, amt, prob = mm.group(1), mm.group(2), float(mm.group(3))
                    if iid not in items:
                        bad.append(f"{f} {cur}: 未知物品 {iid}")
                    if not (0 < prob <= 1):
                        bad.append(f"{f} {cur}: 概率异常 {prob}")
                    if not re.match(r"^\d+(-\d+)?$", amt):
                        bad.append(f"{f} {cur}: 数量格式异常 {amt!r}")
    print()
print("问题:", bad if bad else "无")
