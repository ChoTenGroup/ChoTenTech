# -*- coding: utf-8 -*-
"""补充扫描：quest select 作用域 / 裸 progress / 错误否定 / 其他残留模式（只读）"""
import io, os, re, sys

ROOT = r"D:\Chemdah"
CONVS = os.path.join(ROOT, "core", "conversation")
QUESTS = os.path.join(ROOT, "core", "quest")

def walk(base):
    out = []
    for dp, _, fs in os.walk(base):
        for f in sorted(fs):
            if f.endswith(".yml"):
                out.append(os.path.join(dp, f))
    return sorted(out)

def read_lines(p):
    with io.open(p, encoding="utf-8") as fh:
        return fh.readlines()

# 任务ID集合（文件名）
QUEST_IDS = set(os.path.splitext(os.path.basename(f))[0] for f in walk(QUESTS))

print("=" * 60)
print("P1. 错误否定形式: quest not accepted / quest not completed")
print("=" * 60)
n = 0
for p in walk(CONVS) + walk(QUESTS):
    rel = os.path.relpath(p, ROOT)
    for i, ln in enumerate(read_lines(p), 1):
        if re.search(r"\bquest\s+not\s+(accepted|completed|any|all)", ln):
            print(f"  {rel}:{i}: {ln.strip()[:150]}")
            n += 1
print(f"  共 {n} 处")

print()
print("=" * 60)
print("P2. 含 check quest progress / quest progress 但整行无 quest select（裸进度）")
print("=" * 60)
n = 0
for p in walk(CONVS) + walk(QUESTS):
    rel = os.path.relpath(p, ROOT)
    for i, ln in enumerate(read_lines(p), 1):
        if re.search(r"check quest progress|quest progress", ln) and not re.search(r"quest select", ln):
            # 排除目标行/说明文本（非脚本行）
            stripped = ln.strip()
            if stripped.startswith(("- if:", "- then:", "then: |", "when:", "if:")) or "quest progress" in stripped and ("goal:" in stripped or "addon" in stripped or "chotenui" in stripped):
                pass
            print(f"  {rel}:{i}: {stripped[:150]}")
            n += 1
print(f"  共 {n} 处")

print()
print("=" * 60)
print("P3. all/any 内部残留 { quest select ... } 与外部 progress 并存（作用域问题）")
print("=" * 60)
n = 0
for p in walk(CONVS) + walk(QUESTS):
    rel = os.path.relpath(p, ROOT)
    for i, ln in enumerate(read_lines(p), 1):
        if re.search(r"\{ quest select", ln) and re.search(r"quest progress", ln):
            # 判断 select 是否在 all/any 内部（前缀模式）
            m = re.search(r"all \[|any \[", ln)
            sel = re.search(r"\{ quest select", ln)
            if m and sel and m.start() < sel.start():
                print(f"  {rel}:{i}: {ln.strip()[:150]}")
                n += 1
print(f"  共 {n} 处")

print()
print("=" * 60)
print("P4. 裸 quest accepted / quest completed（整行无 select，且不在 select 前缀行内）")
print("=" * 60)
n = 0
for p in walk(CONVS) + walk(QUESTS):
    rel = os.path.relpath(p, ROOT)
    for i, ln in enumerate(read_lines(p), 1):
        stripped = ln.strip()
        if re.search(r"\b(quest|quests)\s+(accepted|completed)\b", stripped) and not re.search(r"quest select", stripped):
            # 只有脚本行才检查
            if re.match(r"^- if:|^- then:|^if:|^when:|^then:", stripped) or "{" in stripped:
                print(f"  {rel}:{i}: {stripped[:150]}")
                n += 1
print(f"  共 {n} 处")

print()
print("=" * 60)
print("P5. 引用不存在的任务ID的 quest select")
print("=" * 60)
n = 0
for p in walk(CONVS) + walk(QUESTS):
    rel = os.path.relpath(p, ROOT)
    for i, ln in enumerate(read_lines(p), 1):
        for m in re.finditer(r"quest select\s+([A-Za-z0-9_]+)", ln):
            qid = m.group(1)
            if qid not in QUEST_IDS:
                print(f"  {rel}:{i}: {ln.strip()[:150]}")
                n += 1
print(f"  共 {n} 处")
