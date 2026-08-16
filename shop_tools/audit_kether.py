# -*- coding: utf-8 -*-
"""Chemdah 任务/对话/脚本审计器（只读，不修改任何文件）。

检查项：
A. YAML 可解析
B. 任务结构：objective 合法性、goal/condition 必填字段、数字校验
C. 引用有效性：物品ID(itemoutputnames)、实体ID(entity.md)、NPC(NPC.md)、任务ID(quest文件)、
   对话节点 goto/open、trigger 触发ID
D. Kether 静态检查：括号/引号平衡、all/any 元素包裹、未知关键字（INFO）、
   quest select/trigger/goto/open 提取
E. 输出每种 objective 的 condition/goal 样本供人工复核
"""
import io
import os
import re
import sys
import collections
import yaml

ROOT = r"D:\Chemdah"
QUESTS = os.path.join(ROOT, "core", "quest")
CONVS = os.path.join(ROOT, "core", "conversation")
NAMES_FILE = os.path.join(ROOT, "itemoutputnames.yml")
NPC_FILE = os.path.join(ROOT, "story", "NPC.md")
ENTITY_FILE = os.path.join(ROOT, "entity.md")

# ---------------- 参考数据 ----------------
def load_names():
    names = set()
    for line in io.open(NAMES_FILE, encoding="utf-8"):
        line = line.strip()
        if not line or ":" not in line:
            continue
        names.add(line.split(":", 1)[0].strip())
    return names

def load_npcs():
    npcs = set()
    for line in io.open(NPC_FILE, encoding="utf-8"):
        m = re.match(r"^NPCID:\s*([A-Za-z0-9_]+)", line)
        if m:
            npcs.add(m.group(1))
    return npcs

def load_entities():
    ents = set()
    for line in io.open(ENTITY_FILE, encoding="utf-8"):
        for m in re.finditer(r"\b(Smiler|Deathmoths|Wretches|Clump|Dullers|Hound)\d?\b", line):
            ents.add(m.group(0))
    # 补齐 1~5 序列
    out = set()
    for e in ents:
        base = re.match(r"([A-Za-z]+)\d*", e).group(1)
        if base == "Deathmoths":
            out.add("Deathmoths")
            out.add("Deathmoths1")
        else:
            for i in range(1, 6):
                out.add(f"{base}{i}")
    return out

ITEMS = load_names()
NPCS = load_npcs()
ENTS = load_entities()

# ---------------- 已知 objective 与 schema ----------------
KNOWN_OBJECTIVES = {
    "choten submit", "choten reach level", "choten noclip", "choten noclip enter",
    "player data", "player real move", "player kill",
    "mythicmobs kill", "ranged mythicmobs kill",
    "block interact", "block break", "block place",
    "trigger", "placeholder api", "take water",
    "lithium item grant", "lithium loot create", "lithium",
}

# Kether 关键字白名单（来自 kether.md + 仓库实际使用，仅作 INFO 参考）
KNOWN_KW = {
    "profile", "var", "check", "if", "all", "any", "not", "quest", "quests",
    "inventory", "command", "tell", "math", "inline", "repeat", "sleep",
    "while", "trigger", "script", "open", "goto", "close", "talk", "set",
    "print", "title", "affection", "submititem", "cttshop", "papi", "si",
    "give", "take", "stop", "run", "using", "close-task", "complete-task",
    "accept", "accept-check", "complete", "fail", "reset", "track", "count",
    "tasks", "accepted", "completed", "progress", "value", "target",
    "percent", "percent100", "data", "keys", "save", "default", "to", "add",
    "papi", "money", "point", "repumeg", "repubntg", "repuuec", "as",
    "console", "player", "color", "by", "hold", "anvil", "and", "or",
    "== ", "!=", ">=", "<=", ">=  ", "then", "else", "in", "with", "on",
    "enter", "exit", "dead", "quit", "join", "async", "world", "location",
    "spawn", "sound", "playsound", "stop", "send", "message", "msg",
    "quests", "grant", "give", "item", "loot", "create", "noclip", "water",
    "real", "move", "interact", "break", "place", "kill", "ranged", "api",
    "lithium", "reach", "level", "heal", "feed", "hurt", "glow", "bless",
    "curse", "power", "defense", "attack", "dodge", "crit", "absorb",
}

def qpath(base, sub=""):
    return os.path.join(base, sub)

# ---------------- 收集文件 ----------------
def collect_yaml(root):
    out = []
    for dirpath, _, files in os.walk(root):
        for f in sorted(files):
            if f.endswith(".yml"):
                out.append(os.path.join(dirpath, f))
    return sorted(out)

QUEST_FILES = collect_yaml(QUESTS)
CONV_FILES = collect_yaml(CONVS)

# 全部任务 ID（文件名 = 任务ID）
QUEST_IDS = set(os.path.splitext(os.path.basename(f))[0] for f in QUEST_FILES)
# 全部对话 ID（文件名 = 对话ID，open 目标）
CONV_IDS = set(os.path.splitext(os.path.basename(f))[0] for f in CONV_FILES)

# 全部对话节点名
NODE_NAMES = set()
CONV_NODES = {}  # file -> set(nodes)
for fp in CONV_FILES:
    rel = os.path.relpath(fp, CONVS).replace("\\", "/")
    try:
        data = yaml.safe_load(io.open(fp, encoding="utf-8"))
    except Exception:
        continue
    if not isinstance(data, dict):
        continue
    nodes = set()
    for k in data:
        if k != "__option__":
            nodes.add(k)
    CONV_NODES[rel] = nodes
    NODE_NAMES |= nodes

# 全部 trigger id（任务 trigger 条件的 value）
TRIGGER_IDS = set()
for fp in QUEST_FILES:
    try:
        data = yaml.safe_load(io.open(fp, encoding="utf-8"))
    except Exception:
        continue
    for qid, q in (data or {}).items():
        tasks = q.get("task") if isinstance(q, dict) else None
        if not isinstance(tasks, dict):
            continue
        for tn, t in tasks.items():
            if not isinstance(t, dict):
                continue
            if str(t.get("objective", "")).strip().strip('"') == "trigger":
                cond = t.get("condition")
                if isinstance(cond, dict):
                    v = cond.get("value")
                    if v:
                        TRIGGER_IDS.add(str(v))

# ---------------- Kether 静态检查 ----------------
BRACE_O = "{"; BRACE_C = "}"
PAREN_O = "("; PAREN_C = ")"
BRACK_O = "["; BRACK_C = "]"

def kether_static(script, loc, findings, infos):
    """script: 单行或多行 Kether 文本。"""
    if not script:
        return
    s = script.strip()
    if not s:
        return
    # 1. 平衡检查
    for o, c, nm in ((BRACE_O, BRACE_C, "{}"), (PAREN_O, PAREN_C, "()"),
                     (BRACK_O, BRACK_C, "[]")):
        if s.count(o) != s.count(c):
            findings.append(f"{loc} 括号不平衡 {nm}: {s.count(o)} vs {s.count(c)}\n    {s[:120]}")
    # 引号平衡（粗查）
    if s.count('"') % 2 != 0:
        findings.append(f"{loc} 双引号数量为奇数\n    {s[:120]}")

    # 2. all/any 元素检查
    for m in re.finditer(r"\b(all|any)\s*\[", s):
        i = m.end()
        depth = 1
        while i < len(s) and depth > 0:
            if s[i] == "[": depth += 1
            elif s[i] == "]": depth -= 1
            i += 1
        body = s[m.end():i - 1]
        # 找未用 {} 包裹的元素
        j = 0
        while j < len(body):
            if body[j] == "{":
                d = 1; k = j + 1
                while k < len(body) and d > 0:
                    if body[k] == "{": d += 1
                    elif body[k] == "}": d -= 1
                    k += 1
                j = k
            elif body[j].strip():
                k = j
                while k < len(body) and body[k] != "{":
                    k += 1
                token = body[j:k].strip()
                if token:
                    words = token.split()
                    first = words[0] if words else ""
                    second = words[1] if len(words) > 1 else ""
                    # kether.md 推荐模式: quest select X any [ quest accepted quest completed ]
                    # 布尔动作(quest accepted/completed、inventory check、check/if/not/all/any)可省 {}
                    if first == "quest" and second == "select":
                        infos.append(f"{loc} all/any 内出现 quest select（应置于 any/all 之外）:\n    {token[:100]}")
                    elif first == "quests" and second == "select":
                        infos.append(f"{loc} all/any 内出现 quests select（应置于 any/all 之外）:\n    {token[:100]}")
                    elif first == "inventory" and second != "check":
                        infos.append(f"{loc} all/any 中未用{{}}包裹的 inventory 动作（可能返回 Unit）:\n    {token[:100]}")
                    elif first in ("trigger", "var", "profile", "command", "script"):
                        infos.append(f"{loc} all/any 中未用{{}}包裹的 {first} 动作（可能返回 Unit）:\n    {token[:100]}")
                    elif first in ("check", "if", "not", "all", "any", "math",
                                   "quest", "quests", "inventory"):
                        pass  # 返回布尔，允许省略
                    else:
                        infos.append(f"{loc} all/any 未包裹元素首词未知: {token[:80]}")
                j = k
            else:
                j += 1

    # 3. 逐行首词检查（多行脚本）
    for ln in s.splitlines():
        t = ln.strip()
        if not t or t.startswith("#"):
            continue
        # 跳过 if/all/any/command 等带子句的行
        m = re.match(r"^([A-Za-z][A-Za-z0-9_-]*)\b", t)
        if m and m.group(1) not in KNOWN_KW:
            infos.append(f"{loc} 未知动作关键字: {m.group(1)} -> {t[:100]}")

    # 4. 引用提取
    for q in re.finditer(r"quest select\s+([A-Za-z0-9_]+)", s):
        qid = q.group(1)
        if qid not in QUEST_IDS:
            findings.append(f"{loc} quest select 引用不存在的任务: {qid}\n    {s[:120]}")
    for g in re.finditer(r"goto\s+([A-Za-z0-9_]+)", s):
        pass  # 目标节点在调用方检查（需要知道所在文件）
    return

# ---------------- 主流程 ----------------
def main():
    findings = []
    infos = []
    obj_samples = collections.defaultdict(list)  # objective -> [(file:line, 结构)]

    # A/B/C: 任务
    for fp in QUEST_FILES:
        rel = os.path.relpath(fp, ROOT).replace("\\", "/")
        try:
            data = yaml.safe_load(io.open(fp, encoding="utf-8"))
        except yaml.YAMLError as e:
            findings.append(f"{rel} YAML 解析失败: {e}")
            continue
        if not isinstance(data, dict):
            findings.append(f"{rel} 根节点非 map")
            continue
        for qid, q in data.items():
            if not isinstance(q, dict):
                findings.append(f"{rel} 任务 {qid} 非 map")
                continue
            meta = q.get("meta")
            if not isinstance(meta, dict) or not meta.get("name"):
                findings.append(f"{rel} 任务 {qid} 缺少 meta.name")
            tasks = q.get("task")
            if not isinstance(tasks, dict):
                findings.append(f"{rel} 任务 {qid} 缺少 task")
                continue
            for tn, t in tasks.items():
                if not isinstance(t, dict):
                    findings.append(f"{rel} 任务 {qid} 条目 {tn} 非 map")
                    continue
                if not isinstance(t.get("meta"), dict) or not t["meta"].get("name"):
                    findings.append(f"{rel} 任务 {qid} 条目 {tn} 缺少 meta.name")
                obj = str(t.get("objective", "")).strip().strip('"').strip()
                if not obj:
                    findings.append(f"{rel} 任务 {qid} 条目 {tn} 缺少 objective")
                    continue
                if obj not in KNOWN_OBJECTIVES:
                    findings.append(f"{rel} 任务 {qid} 条目 {tn} 未知 objective: {obj!r}")
                cond = t.get("condition")
                goal = t.get("goal")
                g = goal if isinstance(goal, dict) else {}
                if not isinstance(goal, dict):
                    findings.append(f"{rel} 任务 {qid} 条目 {tn} goal 非 map")
                # goal.amount 数字
                if "amount" in g and not isinstance(g["amount"], (int, float)):
                    findings.append(f"{rel} 任务 {qid} 条目 {tn} goal.amount 非数字: {g['amount']!r}")
                # 按 objective 校验
                if obj in ("choten submit",):
                    if not isinstance(cond, dict):
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} choten submit 缺少 condition")
                    else:
                        it = cond.get("item")
                        if it:
                            iid = str(it).split(":")[-1]
                            if iid not in ITEMS:
                                findings.append(f"{rel} 任务 {qid} 条目 {tn} 物品不存在: {it}")
                        npc = cond.get("npc")
                        if npc and str(npc) not in NPCS:
                            findings.append(f"{rel} 任务 {qid} 条目 {tn} npc 不存在于 NPC.md: {npc}")
                    if not isinstance(g.get("amount"), (int, float)):
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} 缺少 goal.amount")
                elif obj in ("mythicmobs kill", "ranged mythicmobs kill"):
                    if isinstance(cond, dict):
                        nm = str(cond.get("name", ""))
                        for ent in nm.split(";"):
                            ent = ent.strip()
                            if ent and ent not in ENTS:
                                findings.append(f"{rel} 任务 {qid} 条目 {tn} 实体不存在于 entity.md: {ent}")
                    else:
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} 缺少 condition")
                    if not isinstance(g.get("amount"), (int, float)):
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} 缺少 goal.amount")
                elif obj == "player data":
                    if not g.get("key"):
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} player data 缺少 goal.key")
                elif obj == "placeholder api":
                    if not g.get("key"):
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} placeholder api 缺少 goal.key")
                    if not g.get("value"):
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} placeholder api 缺少 goal.value")
                elif obj == "trigger":
                    if not (isinstance(cond, dict) and cond.get("value")):
                        findings.append(f"{rel} 任务 {qid} 条目 {tn} trigger 缺少 condition.value")
                # 样本收集（每种 objective 最多 3 个）
                if len(obj_samples[obj]) < 3:
                    obj_samples[obj].append((f"{rel} 任务{qid} 条目{tn}", t))

                # 条目内 agent 脚本
                tagent = t.get("agent")
                if isinstance(tagent, dict):
                    for ev, sc in tagent.items():
                        if isinstance(sc, str):
                            kether_static(sc, f"{rel} 任务{qid} 条目{tn} agent[{ev}]", findings, infos)

            # 任务级 agent
            ag = q.get("agent")
            if isinstance(ag, dict):
                for ev, sc in ag.items():
                    if isinstance(sc, str):
                        kether_static(sc, f"{rel} 任务{qid} agent[{ev}]", findings, infos)
            # addon.restart
            addon = q.get("addon")
            if isinstance(addon, dict) and "restart" in addon:
                r = addon["restart"]
                if not isinstance(r, str) or r.strip() not in ("player dead", "player quit", "player join"):
                    infos.append(f"{rel} 任务{qid} addon.restart 值未识别: {r!r}")

    # 对话
    for fp in CONV_FILES:
        rel = os.path.relpath(fp, CONVS).replace("\\", "/")
        try:
            data = yaml.safe_load(io.open(fp, encoding="utf-8"))
        except yaml.YAMLError as e:
            findings.append(f"conv {rel} YAML 解析失败: {e}")
            continue
        if not isinstance(data, dict):
            findings.append(f"conv {rel} 根节点非 map")
            continue
        if "__option__" not in data:
            findings.append(f"conv {rel} 缺少 __option__")
        else:
            opt = data["__option__"]
            if not isinstance(opt, dict) or "theme" not in opt:
                findings.append(f"conv {rel} __option__ 缺少 theme")
        nodes = {k: v for k, v in data.items() if k != "__option__"}
        for nid, node in nodes.items():
            if not isinstance(node, dict):
                findings.append(f"conv {rel} 节点 {nid} 非 map")
                continue
            # npc id
            nidv = node.get("npc id") or node.get("npc-id")
            if nidv:
                nm = str(nidv).split()[-1]
                if nm not in NPCS:
                    infos.append(f"conv {rel} 节点 {nid} npc id 不在 NPC.md: {nm}")
            # when 块
            when = node.get("when")
            if isinstance(when, list):
                for w in when:
                    if isinstance(w, dict):
                        if "if" in w and isinstance(w["if"], str):
                            kether_static(w["if"], f"conv {rel} {nid} when.if", findings, infos)
                        op = w.get("open")
                        if op and str(op) not in NODE_NAMES:
                            findings.append(f"conv {rel} {nid} when.open 目标节点不存在: {op}")
            # player 回复
            pl = node.get("player")
            if isinstance(pl, list):
                for p in pl:
                    if not isinstance(p, dict):
                        findings.append(f"conv {rel} 节点 {nid} player 条目非 map")
                        continue
                    if "if" in p and isinstance(p["if"], str):
                        kether_static(p["if"], f"conv {rel} {nid} player.if", findings, infos)
                    then = p.get("then")
                    if isinstance(then, str):
                        kether_static(then, f"conv {rel} {nid} player.then", findings, infos)
                        # goto 目标
                        for g in re.finditer(r"\bgoto\s+([A-Za-z0-9_]+)", then):
                            tgt = g.group(1)
                            if tgt not in nodes:
                                findings.append(f"conv {rel} {nid} goto 目标节点不存在(同文件): {tgt}")
                        for o in re.finditer(r"\bopen\s+([A-Za-z0-9_]+)", then):
                            tgt = o.group(1)
                            if tgt not in NODE_NAMES and tgt not in CONV_IDS:
                                findings.append(f"conv {rel} {nid} open 目标对话不存在: {tgt}")
                        # trigger 引用
                        for tr in re.finditer(r"\btrigger\s+([A-Za-z0-9_]+)", then):
                            tid = tr.group(1)
                            if tid not in TRIGGER_IDS:
                                infos.append(f"conv {rel} {nid} trigger 未在任务中找到对应 condition.value: {tid}")
            # npc 文本中的内联表达式
            ntxt = node.get("npc")
            if isinstance(ntxt, str) and "{{" in ntxt and "}}" not in ntxt:
                infos.append(f"conv {rel} 节点 {nid} npc 文本有 {{ 无 }}")

    print("=" * 70)
    print("FINDINGS（确定为问题的）")
    print("=" * 70)
    if findings:
        for f_ in findings:
            print("FIND:", f_.replace("\n", "\n     "))
        print(f"\n共 {len(findings)} 条 FIND")
    else:
        print("无 FIND")
    print()
    print("=" * 70)
    print(f"INFO（疑似/需人工复核）共 {len(infos)} 条")
    print("=" * 70)
    for i in infos:
        print("INFO:", i.replace("\n", "\n     "))
    print()
    print("=" * 70)
    print("objective 样本（人工复核 schema）")
    print("=" * 70)
    for obj in sorted(obj_samples):
        print(f"\n--- {obj} ---")
        for loc, t in obj_samples[obj]:
            cond = t.get("condition")
            goal = t.get("goal")
            print(f"  {loc}")
            if cond is not None:
                print(f"    condition: {cond}")
            if goal is not None:
                print(f"    goal: {goal}")
    print()
    print(f"统计: quest文件 {len(QUEST_FILES)}, 对话文件 {len(CONV_FILES)}, "
          f"任务ID {len(QUEST_IDS)}, triggerID {len(TRIGGER_IDS)}, "
          f"对话节点 {len(NODE_NAMES)}, 物品 {len(ITEMS)}, NPC {len(NPCS)}, 实体 {len(ENTS)}")

if __name__ == "__main__":
    main()
