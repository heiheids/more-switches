"""Builds the installable NeoForge jar.

The jar packs compiled classes, the resources, the two mixin configs and the expanded mod metadata.
It is deliberately dependency-free: the mod needs only NeoForge itself, so nothing else is bundled.
"""

import io
import os
import shutil
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PROJECT = r"D:\@临时工程\更多开关功能"
MAIN_OUT = os.path.join(PROJECT, "_build", "nf-main")
RESOURCES = os.path.join(PROJECT, "src", "main", "resources")
TEMPLATE = os.path.join(PROJECT, "src", "main", "templates", "META-INF", "neoforge.mods.toml")
DIST = os.path.join(PROJECT, "_build", "nf-dist")

JAVAC = r"C:\Users\BZX\Documents\deepseek-harness\default-workspace\mcmod\tools\jdk25\bin\javac.exe"

MOD_ID = "moreswitches"
MOD_NAME = "更多开关功能"
MOD_VERSION = "2.0.0"
MOD_AUTHORS = "黑山大叔"
MOD_LICENSE = "MIT"
MOD_DESCRIPTION = ("一页开关式的实用工具模组：窗口锁定、烟花冷却、雨天睡觉、光照预览、"
                   "自动补图腾、各类静音等。作者：黑山大叔 / 主页："
                   "https://space.bilibili.com/19428259 / 核心结构来自 "
                   "https://crates.io/crates/lighty-launcher")

PROPERTIES = {
    "minecraft_version": "1.21.1",
    "minecraft_version_range": "[1.21.1,1.22)",
    "neo_version": "21.1.256",
    "neo_version_range": "[21.1.0,)",
    "loader_version_range": "[4,)",
    "mod_id": MOD_ID,
    "mod_name": MOD_NAME,
    "mod_license": MOD_LICENSE,
    "mod_version": MOD_VERSION,
    "mod_authors": MOD_AUTHORS,
    "mod_description": MOD_DESCRIPTION,
}


def expand(text):
    for key, value in PROPERTIES.items():
        text = text.replace("${" + key + "}", value)
    return text


def build_metadata():
    raw = io.open(TEMPLATE, encoding="utf-8").read()
    return expand(raw)


def add_tree(archive, root, prefix=""):
    count = 0
    for directory, _dirs, files in os.walk(root):
        for name in files:
            full = os.path.join(directory, name)
            rel = os.path.relpath(full, root).replace("\\", "/")
            archive.write(full, prefix + rel)
            count += 1
    return count


def main():
    if not os.path.isdir(MAIN_OUT):
        print("no compiled classes; run nf_compile.py first")
        return 1

    shutil.rmtree(DIST, ignore_errors=True)
    os.makedirs(DIST, exist_ok=True)
    jar_path = os.path.join(DIST, f"{MOD_ID}-{MOD_VERSION}.jar")

    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as archive:
        classes = add_tree(archive, MAIN_OUT, "")
        resources = add_tree(archive, RESOURCES, "")
        archive.writestr("META-INF/neoforge.mods.toml", build_metadata())
        # The icon sits at the jar root so the metadata's logoFile resolves.
        icon = os.path.join(RESOURCES, "assets", MOD_ID, "textures", "icon.png")
        if os.path.exists(icon):
            archive.write(icon, "icon.png")
        print("classes:", classes, "resources:", resources)

    print("jar:", jar_path)
    print("size:", os.path.getsize(jar_path))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
