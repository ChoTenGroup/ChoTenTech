#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量把 ColorUtil 的自定义 <cX> 色标签替换为 MiniMessage 原生 Hex 色标签。

做法: 逐个匹配 <...> (非贪婪), 只把标签内出现的调色板单词 (cblack/cred/...)
替换为对应 hex, 其余单词 (reset/channel/click/gradient 等) 原样保留。

  <cred>                   -> <#E08A98>
  </cred>                  -> </#E08A98>
  <gradient:cwhite:cred>   -> <gradient:#F2F5FA:#E08A98>
  <rainbow:cgreen:caqua>   -> <rainbow:#8FD4A6:#84D1CB>

用法:
    python scripts/replace_color_tags.py                # 默认扫描 src/
    python scripts/replace_color_tags.py --path src      # 指定目录
    python scripts/replace_color_tags.py --dry-run       # 只预览不写文件
"""

import argparse
import re
import sys
from pathlib import Path

# ColorUtil 调色板: c前缀颜色名 -> 柔和 hex
PALETTE = {
    "cblack":        "#2A2F3A",
    "cdark_blue":    "#637FAF",
    "cdark_green":   "#6EA982",
    "cdark_aqua":    "#5FA8A3",
    "cdark_red":     "#B46572",
    "cdark_purple":  "#977FB8",
    "cgold":         "#DFBD7C",
    "cgray":         "#A9B4C3",
    "cdark_gray":    "#6E7A8C",
    "cblue":         "#94AFE8",
    "cgreen":        "#8FD4A6",
    "caqua":         "#84D1CB",
    "cred":          "#E08A98",
    "clight_purple": "#CDAEEF",
    "cyellow":       "#EFDF8F",
    "cwhite":        "#F2F5FA",
}

# 单词匹配: 长名优先, 避免短名把长名拆碎
WORD_RE = re.compile(
    r"\b(?:" + "|".join(sorted(PALETTE, key=len, reverse=True)) + r")\b"
)

# 标签匹配: 非贪婪, 停在第一个 '>' (嵌套标签也各自独立处理)
TAG_RE = re.compile(r"<[^>]+>")

# 待处理的文本扩展名
EXTENSIONS = {".kt", ".kts", ".yml", ".yaml", ".toml", ".conf", ".json"}

# 跳过的目录
SKIP_DIRS = {".git", ".gradle", ".idea", ".kotlin", "build", "node_modules"}


def replace_tag(m: re.Match, counter: list) -> str:
    content = m.group(0)

    def _swap(w: re.Match) -> str:
        counter[0] += 1
        return PALETTE[w.group(0)]

    return WORD_RE.sub(_swap, content)


def process_text(text: str, counter: list) -> str:
    return TAG_RE.sub(lambda m: replace_tag(m, counter), text)


def collect_files(root: Path) -> list:
    return [
        p for p in root.rglob("*")
        if p.is_file()
        and p.suffix.lower() in EXTENSIONS
        and not any(part in SKIP_DIRS for part in p.parts)
    ]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--path", default="src", help="要扫描的目录 (默认: src)")
    ap.add_argument("--dry-run", action="store_true", help="只预览不写文件")
    args = ap.parse_args()

    root = Path(args.path)
    if not root.is_dir():
        print(f"[错误] 目录不存在: {root}", file=sys.stderr)
        return 1

    files = collect_files(root)
    if not files:
        print("[提示] 目录下没有可处理的文件")
        return 0

    changed = 0
    total = 0
    for path in files:
        try:
            raw = path.read_bytes()
        except OSError as e:
            print(f"[跳过] {path}: {e}")
            continue
        text = raw.decode("utf-8-sig", errors="replace")
        counter = [0]
        new_text = process_text(text, counter)
        if counter[0] == 0:
            continue

        if args.dry_run:
            print(f"[待改] {path}  ({counter[0]} 处)")
        else:
            path.write_bytes(new_text.encode("utf-8"))
            print(f"[已改] {path}  ({counter[0]} 处)")
        changed += 1
        total += counter[0]

    print(f"\n共处理 {changed} 个文件, 替换 {total} 处")
    return 0


if __name__ == "__main__":
    sys.exit(main())
