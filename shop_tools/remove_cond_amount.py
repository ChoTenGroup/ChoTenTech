# -*- coding: utf-8 -*-
"""移除所有 choten submit 条目 condition 下的 amount 行（只读 + --apply）。

依据用户裁决：choten submit 的数量只放 goal.amount，condition 不保留 amount。
"""
import io
import os
import re
import sys
import yaml

QUESTS = r"D:\Chemdah\core\quest"


def collect_targets():
    """返回 {path: set(task_no)}：objective==choten submit 且 condition.amount 存在 的条目。"""
    targets = {}
    for root, _, fs in os.walk(QUESTS):
        for f in sorted(fs):
            if not f.endswith(".yml"):
                continue
            path = os.path.join(root, f)
            try:
                data = yaml.safe_load(io.open(path, encoding="utf-8"))
            except Exception:
                continue
            for qid, q in (data or {}).items():
                if not isinstance(q, dict):
                    continue
                for tn, t in (q.get("task") or {}).items():
                    if not isinstance(t, dict):
                        continue
                    obj = str(t.get("objective", "")).strip().strip('"').strip()
                    cond = t.get("condition")
                    if obj == "choten submit" and isinstance(cond, dict) and "amount" in cond:
                        targets.setdefault(path, set()).add(str(tn))
    return targets


TASK_RE = re.compile(r"^(\s*)(\d+):\s*$")
AMOUNT_RE = re.compile(r"^(\s*)amount:\s*(.*)$")


def process(path, task_nos):
    """逐行删除目标任务 condition 块内的 amount 行。返回 (new_lines, removed)。"""
    with io.open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    out = []
    removed = 0
    in_task = None      # 当前所在任务号（仅当在目标集合中）
    in_cond = False     # 是否在 condition: 块内
    cond_indent = None  # condition: 的缩进
    for ln in lines:
        m = TASK_RE.match(ln.rstrip("\r\n"))
        if m and (m.group(1) == "    " or m.group(1) == "  "):
            # 任务键（4 空格缩进，兼容 2 空格）
            if m.group(2) in task_nos and (m.group(1) == "    " or m.group(1) == "  "):
                in_task = m.group(2)
            else:
                in_task = None
            in_cond = False
            out.append(ln)
            continue
        if in_task is not None:
            cm = re.match(r"^(\s*)condition:\s*$", ln)
            if cm:
                in_cond = True
                cond_indent = len(cm.group(1))
                out.append(ln)
                continue
            if in_cond:
                stripped = ln.strip()
                indent = len(ln) - len(ln.lstrip())
                if not stripped:
                    out.append(ln)
                    continue
                if indent > cond_indent:
                    am = re.match(r"^(\s*)amount:\s*", ln)
                    if am:
                        removed += 1
                        continue
                    out.append(ln)
                    continue
                else:
                    in_cond = False
                    # 落入下面正常处理
        out.append(ln)
    return out, removed


def main():
    apply_changes = "--apply" in sys.argv
    targets = collect_targets()
    total_removed = 0
    files = []
    for path, task_nos in sorted(targets.items()):
        new_lines, removed = process(path, task_nos)
        if removed:
            rel = os.path.relpath(path, QUESTS)
            files.append(rel)
            total_removed += removed
            print(f"{rel}: 移除 {removed} 行 (任务 {sorted(task_nos)})")
            if apply_changes:
                with io.open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.writelines(new_lines)
    print()
    print(f"文件数 {len(files)}，共移除 {total_removed} 行")
    if not apply_changes:
        print("(dry-run; rerun with --apply to write)")


if __name__ == "__main__":
    main()
