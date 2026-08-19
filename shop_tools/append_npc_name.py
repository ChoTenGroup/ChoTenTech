# -*- coding: utf-8 -*-
"""为所有 choten submit 任务 meta.name 追加 '给<NPC中文名>'。
跳过：npc 为空的条目；名称已以'给<NPC名>'结尾的条目。
用法：append_npc_name.py [--apply]
"""
import io
import os
import re
import sys
import yaml

QUESTS = r"D:\Chemdah\core\quest"

# NPC ID -> 中文名（供名称追加）。来源 NPC.md 展示名 + 必要补充。
NPC = {
    "AkaCandy": "阿卡糖", "AmeRain": "雨糖", "BlackSmith": "布莱克",
    "BoFeng": "波风", "DaoGe": "刀哥", "Greybird": "灰绑",
    "HeDe": "何德", "HuGe": "虎哥", "Ice": "冰块", "Jimmy": "吉米",
    "Kael": "凯尔", "Lefty": "小左", "LiuHuo": "刘火", "LiuWei": "刘维",
    "Luka": "露卡", "MaoSe": "毛瑟", "QianXia": "千夏", "Rhodes": "罗德斯",
    "TeLin": "特林", "TheAchu": "阿初", "Vance": "万斯", "Vulcan": "火神",
    "XingChen": "星辰", "YiChan": "遗产", "ZhangShun": "张顺",
    "ZhiChen": "知陈", "huxi": "胡西", "xinan": "西南", "xite": "西特",
}

TOP_RE = re.compile(r"^([A-Za-z0-9_]+):\s*$")
TASK_RE = re.compile(r"^ {4}(\d+):\s*$")
NAME_RE = re.compile(r"^(\s*name:\s*)([\"']?)(.*?)([\"']?)\s*$", re.S)


def collect_targets():
    """返回 {path: {qid: {task_no: (old_name, new_name)}}}（只含需改名条目）。"""
    out = {}
    for root, _, fs in os.walk(QUESTS):
        for f in sorted(fs):
            if not f.endswith(".yml"):
                continue
            path = os.path.join(root, f)
            try:
                data = yaml.safe_load(io.open(path, encoding="utf-8"))
            except Exception:
                continue
            if not isinstance(data, dict):
                continue
            for qid, q in data.items():
                if not isinstance(q, dict) or not isinstance(q.get("task"), dict):
                    continue
                for tn, t in q["task"].items():
                    if not isinstance(t, dict):
                        continue
                    obj = str(t.get("objective", "")).strip().strip('"').strip()
                    if obj != "choten submit":
                        continue
                    cond = t.get("condition")
                    npc = cond.get("npc") if isinstance(cond, dict) else None
                    name = (t.get("meta") or {}).get("name")
                    if not isinstance(name, str) or not npc:
                        continue
                    cn = NPC.get(npc)
                    if not cn:
                        continue
                    if name.endswith("给" + cn):
                        continue  # 已含
                    new_name = name + "给" + cn
                    out.setdefault(path, {})
                    out[path].setdefault(qid, {})[str(tn)] = (name, new_name)
    return out


def replace_in_file(path, qid_tasks):
    """按行替换指定 (qid, task) 的 meta.name。返回 (out_lines, changed)。"""
    with io.open(path, encoding="utf-8") as fh:
        lines = fh.readlines()

    changed = 0
    cur_qid = None
    cur_task = None
    cur_task_repl = None
    in_meta = False
    out = []

    for ln in lines:
        if ln.strip() == "" or ln.lstrip().startswith("#"):
            out.append(ln)
            continue

        # 顶层键（缩进0）决定当前 qid
        if not ln[0].isspace():
            tm = TOP_RE.match(ln.rstrip("\r\n"))
            cur_qid = tm.group(1) if tm else None
            cur_task = None
            cur_task_repl = None
            in_meta = False
            out.append(ln)
            continue

        indent = len(ln) - len(ln.lstrip())

        if indent == 4:
            tm = TASK_RE.match(ln.rstrip("\r\n"))
            cur_task = tm.group(1) if tm else None
            cur_task_repl = None
            if cur_qid and cur_task and cur_task in qid_tasks.get(cur_qid, {}):
                cur_task_repl = qid_tasks[cur_qid][cur_task][1]
            in_meta = False
            out.append(ln)
            continue

        if indent == 6:
            if ln.strip() == "meta:":
                in_meta = True
            else:
                in_meta = False
            out.append(ln)
            continue

        # 目标任务 meta 块内的 name
        if in_meta and cur_task_repl is not None and indent == 8:
            nm = NAME_RE.match(ln.rstrip("\r\n"))
            if nm and nm.group(3) in (qid_tasks[cur_qid][cur_task][0],):
                prefix, q0, _old, q1 = nm.groups()
                rebuilt = prefix + q0 + cur_task_repl + q1
                out.append(rebuilt + "\n")
                changed += 1
                continue

        out.append(ln)

    return out, changed


def main():
    apply_changes = "--apply" in sys.argv
    targets = collect_targets()
    total = 0
    files = 0
    for path, qid_tasks in sorted(targets.items()):
        new_lines, changed = replace_in_file(path, qid_tasks)
        want = sum(len(v) for v in qid_tasks.values())
        if changed != want:
            print(f"[警告] {os.path.relpath(path, QUESTS)}: 预期 {want} 行，实际 {changed} 行")
        total += changed
        files += 1
        print(f"{os.path.relpath(path, QUESTS)}: 改 {changed}/{want} 行")
        if apply_changes:
            with io.open(path, "w", encoding="utf-8", newline="") as fh:
                fh.writelines(new_lines)
    print()
    print(f"文件数 {files}，共替换 {total} 行")
    if not apply_changes:
        print("(dry-run; rerun with --apply to write)")


if __name__ == "__main__":
    main()