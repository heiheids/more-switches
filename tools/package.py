#!/usr/bin/env python3
"""Build the delivery zip: mod jar + sources + both-language manual + tech notes + lessons.

Layout inside the zip:

    更多开关功能/
        more-switches-1.0.0.jar            可直接放进 mods 的成品
        源码/                              build.gradle 与 src/
        文档/使用说明-中英文.txt
        文档/技术说明.txt
        文档/工作中有价值的经验和踩坑总结.txt
        文档/界面-中文.png
        文档/界面-英文.png
        README.md / LICENSE / mod_context.md

Only this project directory is read or written. Regenerable directories
(_build, build, .gradle, 更多开关功能工程备份) are never included.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DIST_DIR = PROJECT_ROOT / "dist"
PACKAGE_NAME = "更多开关功能"

EXCLUDED_DIRS = {
    "_build",
    "build",
    ".gradle",
    ".gradle-home",
    "dist",
    "更多开关功能工程备份",
    "我的世界skill261029",
    "__pycache__",
    ".git",
}

SOURCE_ITEMS = ("src", "build.gradle", "settings.gradle", "gradle.properties", "LICENSE")
ROOT_DOCS = ("README.md", "mod_context.md")
DOCS_DIR = "docs"

DOC_RENAMES = {
    "使用说明-中英文.txt": "使用说明-中英文.txt",
    "技术说明.txt": "技术说明.txt",
    "经验与踩坑总结.txt": "工作中有价值的经验和踩坑总结.txt",
    "界面-中文.png": "界面-中文.png",
    "界面-英文.png": "界面-英文.png",
}


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def find_jar() -> Path:
    libs = PROJECT_ROOT / "build" / "libs"
    candidates = sorted(libs.glob("more-switches-*.jar"))
    jars = [c for c in candidates if not c.name.endswith("-sources.jar")]
    if not jars:
        fail(f"no mod jar in {libs}; run the gradle build first")
    return jars[-1]


def iter_sources() -> list[Path]:
    files: list[Path] = []
    for item in SOURCE_ITEMS:
        path = PROJECT_ROOT / item
        if not path.exists():
            continue
        if path.is_file():
            files.append(path)
            continue
        for root, dirs, names in os.walk(path):
            dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]
            for name in names:
                files.append(Path(root) / name)
    return files


def iter_docs() -> list[tuple[Path, str]]:
    docs_dir = PROJECT_ROOT / DOCS_DIR
    if not docs_dir.is_dir():
        fail(f"missing docs directory: {docs_dir}")
    found: list[tuple[Path, str]] = []
    for source_name, archive_name in DOC_RENAMES.items():
        path = docs_dir / source_name
        if not path.is_file():
            fail(f"missing documentation file: {path}")
        found.append((path, archive_name))
    return found


def main() -> int:
    jar = find_jar()
    sources = iter_sources()
    docs = iter_docs()
    if not sources:
        fail("no source files found")

    if DIST_DIR.exists():
        shutil.rmtree(DIST_DIR)
    DIST_DIR.mkdir(parents=True)

    zip_path = DIST_DIR / f"{PACKAGE_NAME}-1.0.0.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        base = PACKAGE_NAME
        archive.write(jar, f"{base}/{jar.name}")

        for path in sorted(sources):
            archive.write(path, f"{base}/源码/{path.relative_to(PROJECT_ROOT).as_posix()}")

        for path, archive_name in docs:
            archive.write(path, f"{base}/文档/{archive_name}")

        for name in ROOT_DOCS:
            path = PROJECT_ROOT / name
            if path.is_file():
                archive.write(path, f"{base}/{name}")

    size_mb = zip_path.stat().st_size / (1024 * 1024)
    print(f"package : {zip_path}")
    print(f"size    : {size_mb:.2f} MB")
    print(f"entries : {len(sources)} source files, {len(docs)} documents, 1 jar")

    # The delivery has to stay in the "few hundred KB to a couple of MB" range; anything much
    # bigger means a cache directory slipped in.
    if size_mb > 8:
        print("WARNING: the package is larger than expected -- check for an included cache", file=sys.stderr)

    with zipfile.ZipFile(zip_path) as archive:
        names = archive.namelist()
        jar_entries = [n for n in names if n.endswith(".jar")]
        if len(jar_entries) != 1:
            print(f"WARNING: expected exactly one jar, found {jar_entries}", file=sys.stderr)
        if not any("使用说明" in n for n in names):
            print("WARNING: the usage manual is missing from the archive", file=sys.stderr)
        # A resource check that does not need the game: both language files must be inside the jar.
        with zipfile.ZipFile(jar) as mod:
            mod_names = mod.namelist()
            for required in ("assets/moreswitches/lang/zh_cn.json",
                             "assets/moreswitches/lang/en_us.json",
                             "fabric.mod.json",
                             "moreswitches.mixins.json",
                             "moreswitches.client.mixins.json",
                             "assets/moreswitches/textures/icon.png"):
                if required not in mod_names:
                    print(f"WARNING: {required} is missing from the jar", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
