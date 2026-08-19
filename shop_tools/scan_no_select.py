# -*- coding: utf-8 -*-
"""扫描含 all/any + check quest progress、但无 quest select 前缀的条件。"""
import io
import os
import re

CONVS = r"D:\Chemdah\core\conversation"

files = []
for dp, _, fs in os.walk(CONVS):
    for f in sorted(fs):
        if f.endswith(".yml"):
            files.append(os.path.join(dp, f))

pat = re.compile(r"^\s*-?\s*if:\s*\"?(.*?)\"?\s*$")
hits = []
for p in files:
    rel = os.path.relpath(p, CONVS)
    for i, ln in enumerate(io.open(p, encoding="utf-8"), 1):
        s = ln.strip()
        if not (s.startswith("- if:") or s.startswith("if:")):
            continue
        m = pat.match(ln.rstrip("\n"))
        if not m:
            continue
        cond = m.group(1)
        if re.search(r"\b(all|any)\s*\[", cond) and re.search(r"quest progress", cond):
            has_prefix = bool(
                re.match(r"\s*quest select \b", cond)
                or re.match(r"\s*quests select \b", cond)
            )
            # 首元素是否自包含：all [ { quest select X ... }
            has_self_sel = bool(
                re.match(r"\s*(all|any)\s*\[\s*\{\s*quest(s)?\s*select\s", cond)
            )
            hits.append((rel, i, has_prefix, has_self_sel, s))

print("含 all/any + progress 的条件总数:", len(hits))
print(
    "有前缀:", sum(1 for h in hits if h[2]),
    "| 含自包含select:", sum(1 for h in hits if h[3]),
    "| 两者皆无:", sum(1 for h in hits if not h[2] and not h[3]),
)
print()
print("=== 无前缀select 且 无自包含select（真正缺选择）===")
n = 0
for rel, i, hp, hss, s in hits:
    if not hp and not hss:
        n += 1
        print(f"{rel}:{i}")
        print("   ", s[:200])
print()
print("真正缺选择总数:", n)
print("（无前缀select 但有自包含元素select的：", sum(1 for h in hits if not h[2] and h[3]), "）")