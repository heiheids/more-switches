#!/usr/bin/env python3
"""Source backup before every edit, as required by the project workflow.

Writes one zip per operation into `更多开关功能工程备份/`:

    backup-YYYYMMDD-HHMMSS-<slug>.zip    all source + build files
    backup-YYYYMMDD-HHMMSS-<slug>.txt    what is about to be done

Only this project directory is touched. Build caches (`_build`, `.gradle-home`,
`build`) are excluded so the archive stays in the hundreds-of-KB range.

Usage:
    python tools/backup.py "what I am about to change"
"""

from __future__ import annotations

import datetime
import os
import sys
import zipfile
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BACKUP_DIR = PROJECT_ROOT / "更多开关功能工程备份"

# Directories that are either regenerable or huge; never worth archiving.
EXCLUDED_DIRS = {
    "_build",
    "build",
    ".gradle",
    ".gradle-home",
    "更多开关功能工程备份",
    "我的世界skill261029",
    "__pycache__",
    ".git",
}

# Only these files at the project root are source-ish; everything else is skipped.
ROOT_FILES = {
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "mod_context.md",
    "README.md",
}


def iter_payload() -> list[Path]:
    files: list[Path] = []
    for name in sorted(ROOT_FILES):
        candidate = PROJECT_ROOT / name
        if candidate.is_file():
            files.append(candidate)
    for base in ("src", "gradle", "tools", "docs"):
        base_dir = PROJECT_ROOT / base
        if not base_dir.is_dir():
            continue
        for root, dirs, names in os.walk(base_dir):
            dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]
            for name in names:
                files.append(Path(root) / name)
    return files


def slugify(text: str) -> str:
    keep = [ch if (ch.isalnum() or ch in "-_") else "-" for ch in text.strip()]
    slug = "".join(keep).strip("-")
    while "--" in slug:
        slug = slug.replace("--", "-")
    return (slug[:40] or "noop")


def main() -> int:
    description = " ".join(sys.argv[1:]).strip() or "未说明的操作"
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    slug = slugify(description)

    payload = iter_payload()
    if not payload:
        print("no source files yet; wrote the note only", file=sys.stderr)

    zip_path = BACKUP_DIR / f"backup-{stamp}-{slug}.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in payload:
            archive.write(path, path.relative_to(PROJECT_ROOT).as_posix())

    note_path = BACKUP_DIR / f"backup-{stamp}-{slug}.txt"
    stamp_human = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    lines = [
        f"备份时间: {stamp_human}",
        f"备份内容: {zip_path.name}",
        f"文件数量: {len(payload)}",
        "",
        "即将进行的操作:",
        description,
        "",
        "说明: 本文件与同名 zip 成对生成；zip 为操作前的源码快照，用于回滚。",
        "作者: 黑山大叔  https://space.bilibili.com/19428259",
        "",
    ]
    note_path.write_text("\n".join(lines), encoding="utf-8")

    print(f"backup zip : {zip_path}")
    print(f"backup note: {note_path}")
    print(f"files      : {len(payload)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
