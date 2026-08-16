import os
import re
import sys

CONV_DIR = r"D:\Chemdah\core\conversation"

PREFIX_RE = re.compile(r"^quest select ([A-Za-z0-9_]+) (all|any) \[")
BLOCK_RE = re.compile(r"\b(all|any) \[")
SELECT_RE = re.compile(r"\{ quest select ([A-Za-z0-9_]+) (.*?)\}", re.S)
PROGRESS_RE = re.compile(r"check quest progress|\bquest progress")
IF_RE = re.compile(r"^(\s*- if:\s*)(.*)$")
SIMPLE_CHECKS = ("quest accepted", "quest completed")


def find_block_end(s, open_idx):
    """open_idx = index of '['. Return index of matching ']' or None. Skips {..} regions entirely."""
    depth = 0
    i = open_idx
    n = len(s)
    while i < n:
        c = s[i]
        if c == "{":
            j = s.find("}", i)
            if j == -1:
                return None
            i = j + 1
            continue
        if c == "[":
            depth += 1
        elif c == "]":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return None


def process_cond(s):
    """Return (new_cond, changed, kind) for one if-condition expression."""
    pm = PREFIX_RE.match(s)
    external = pm.group(1) if pm else None

    bm = BLOCK_RE.search(s)
    if not bm:
        return s, False, None
    block_start = bm.start()
    open_idx = bm.end() - 1  # position of '['
    end = find_block_end(s, open_idx)
    if end is None:
        return s, False, None

    content = s[open_idx + 1:end]

    elements = []
    for em in SELECT_RE.finditer(content):
        elements.append((em.start(), em.end(), em.group(1), em.group(2).strip()))
    if not elements:
        return s, False, None

    # residual text between elements: look for progress without its own select
    plain = ""
    pos = 0
    for st, en, _, _ in elements:
        plain += content[pos:st]
        pos = en
    plain += content[pos:]
    has_bare_progress = bool(PROGRESS_RE.search(plain))

    primary = None
    for _, _, q, r in elements:
        if r in SIMPLE_CHECKS:
            primary = q
            break

    if not (has_bare_progress and primary is not None):
        return s, False, None

    def conv(m):
        q, r = m.group(1), m.group(2).strip()
        if q != primary:
            return m.group(0)
        if r == "quest accepted":
            return "{ quest accepted }"
        if r == "quest completed":
            return "{ quest completed }"
        if r.startswith("check quest progress") or r.startswith("quest progress"):
            return "{ " + r + " }"
        return m.group(0)

    new_content = SELECT_RE.sub(conv, content)
    if external == primary:
        new_s = s[:block_start] + "all [" + new_content + "]" + s[end + 1:]
        kind = "dedup"
    else:
        new_s = s[:block_start] + "quest select " + primary + " all [" + new_content + "]" + s[end + 1:]
        kind = "hoist"
    return new_s, True, kind


def main():
    apply_changes = "--apply" in sys.argv
    total_changed = 0
    kind_counts = {"hoist": 0, "dedup": 0}
    files_changed = set()
    changed_lines = []

    for root, dirs, fs in os.walk(CONV_DIR):
        for f in sorted(fs):
            if not f.endswith(".yml"):
                continue
            path = os.path.join(root, f)
            with open(path, "r", encoding="utf-8") as fh:
                lines = fh.readlines()
            new_lines = []
            changed = False
            for ln in lines:
                im = IF_RE.match(ln.rstrip("\r\n"))
                if not im:
                    new_lines.append(ln)
                    continue
                prefix, raw = im.group(1), im.group(2)
                quoted = raw.startswith('"') and raw.endswith('"')
                cond = raw[1:-1] if quoted else raw
                new_cond, is_changed, kind = process_cond(cond)
                if is_changed:
                    changed = True
                    rebuilt = prefix + ('"' + new_cond + '"' if quoted else new_cond)
                    changed_lines.append((path, ln.rstrip("\r\n"), rebuilt, kind))
                    kind_counts[kind] = kind_counts.get(kind, 0) + 1
                    new_lines.append(rebuilt + "\n")
                else:
                    new_lines.append(ln)
            if changed:
                total_changed += 1
                files_changed.add(path)
                if apply_changes:
                    with open(path, "w", encoding="utf-8", newline="") as fh:
                        fh.writelines(new_lines)

    for path, old, new, kind in changed_lines:
        print(f"[{kind}] {os.path.relpath(path, CONV_DIR)}")
        print(f"  OLD: {old}")
        print(f"  NEW: {new}")
    print()
    print(f"files changed: {total_changed}  lines: {len(changed_lines)}  kinds: {kind_counts}")
    if not apply_changes:
        print("(dry-run; rerun with --apply to write)")


if __name__ == "__main__":
    main()
