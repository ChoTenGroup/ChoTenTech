# -*- coding: utf-8 -*-
"""补充扫描2：choten submit condition.amount 残留 / 引用字符串数字 / quests select 拼写（只读）"""
import io, os, re, yaml

ROOT = r"D:\Chemdah"
QUESTS = os.path.join(ROOT, "core", "quest")

def walk(base):
    out = []
    for dp, _, fs in os.walk(base):
        for f in sorted(fs):
            if f.endswith(".yml"):
                out.append(os.path.join(dp, f))
    return sorted(out)

print("=" * 60)
print("S1. choten submit 条目中 condition 仍含 amount 的（残留）")
print("=" * 60)
n = 0
for p in walk(QUESTS):
    rel = os.path.relpath(p, ROOT)
    try:
        data = yaml.safe_load(io.open(p, encoding="utf-8"))
    except Exception as e:
        print(f"  {rel} 解析失败: {e}")
        continue
    for qid, q in (data or {}).items():
        for tn, t in (q.get("task") or {}).items():
            obj = str(t.get("objective", "")).strip().strip('"').strip()
            if obj == "choten submit":
                cond = t.get("condition") or {}
                if isinstance(cond, dict) and "amount" in cond:
                    print(f"  {rel} 任务{qid} 条目{tn}: condition.amount = {cond['amount']!r}, goal.amount = {t.get('goal',{}).get('amount')!r}")
                    n += 1
print(f"  共 {n} 处")

print()
print("=" * 60)
print("S2. goal.amount 为字符串（非数字）的任务条目")
print("=" * 60)
n = 0
for p in walk(QUESTS):
    rel = os.path.relpath(p, ROOT)
    try:
        data = yaml.safe_load(io.open(p, encoding="utf-8"))
    except Exception:
        continue
    for qid, q in (data or {}).items():
        for tn, t in (q.get("task") or {}).items():
            g = t.get("goal") or {}
            if isinstance(g, dict) and "amount" in g and isinstance(g["amount"], str):
                print(f"  {rel} 任务{qid} 条目{tn}: goal.amount = {g['amount']!r}")
                n += 1
print(f"  共 {n} 处")

print()
print("=" * 60)
print("S3. quests select / 其他拼写变体")
print("=" * 60)
n = 0
for base, label in ((QUESTS, "quest"), (os.path.join(ROOT, "core", "conversation"), "conv")):
    for p in walk(base):
        rel = os.path.relpath(p, ROOT)
        for i, ln in enumerate(io.open(p, encoding="utf-8"), 1):
            if re.search(r"\bquests\s+select\b", ln) or re.search(r"\bcheck\s+quest\s+selected\b", ln):
                print(f"  {rel}:{i}: {ln.strip()[:120]}")
                n += 1
print(f"  共 {n} 处")
