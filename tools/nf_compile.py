"""Compiles the mod against the NeoForge 1.21.1 classpath without starting a Gradle daemon.

The sandbox cannot start Gradle daemons from the shell it provides, and the NeoForge maven is
unreachable from this machine, so the build is driven with javac over jars that are already in the
Gradle cache. That is the same approach earlier Minecraft source builds on this machine used.
"""

import io
import os
import shutil
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PROJECT = r"D:\@临时工程\更多开关功能"
TOOLS = r"C:\Users\BZX\Documents\deepseek-harness\default-workspace\mcmod\tools"
GRADLE = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1")
JDK = os.path.join(TOOLS, "jdk25")
JAVAC = os.path.join(JDK, "bin", "javac.exe")

MAIN_SRC = os.path.join(PROJECT, "src", "main", "java")
TEST_SRC = os.path.join(PROJECT, "src", "test", "java")
MAIN_OUT = os.path.join(PROJECT, "_build", "nf-main")
TEST_OUT = os.path.join(PROJECT, "_build", "nf-test")


def jars(group, artifact, version=None):
    """Every jar of one artifact, skipping sources and javadoc.

    The Gradle cache stores each file under a hash directory, so the version folder has to be walked
    rather than globbed one level deep. ``version=None`` means "the newest version present".
    """
    # The cache keeps one directory per group id verbatim, dots and all.
    base = os.path.join(GRADLE, group, artifact)
    if not os.path.isdir(base):
        return []

    if version is None:
        present = [v for v in os.listdir(base) if os.path.isdir(os.path.join(base, v))]
        if not present:
            return []
        # Sort by dotted numeric parts so 2.10.0 beats 2.9.0 rather than sorting as text.
        present.sort(key=lambda v: [int(p) if p.isdigit() else 0 for p in v.split(".")])
        version = present[-1]

    root = os.path.join(base, version)
    found = []
    for directory, _dirs, files in os.walk(root):
        for name in files:
            if not name.endswith(".jar"):
                continue
            if "sources" in name or "javadoc" in name:
                continue
            found.append(os.path.join(directory, name))
    return found


def classpath():
    parts = []

    minecraft = os.path.join(PROJECT, "_build", "nf-mc", "mc-1.21.1-named.jar")
    if not os.path.exists(minecraft):
        raise SystemExit("missing deobfuscated Minecraft at " + minecraft)
    parts.append(minecraft)

    parts += jars("net.neoforged", "neoforge", "21.1.256")

    # The FML loader carries @Mod and FMLPaths. NeoForge declares it, but the Gradle cache does
    # not hold that artifact, so it is taken from the instance's own libraries. Forward slashes
    # are used on purpose: Windows accepts them and they avoid a layer of escaping.
    instance_loader = ("D:/1.21.1F-NF/.minecraft/libraries/net/neoforged/"
                       "fancymodloader/loader/4.0.45/loader-4.0.45.jar")
    if os.path.exists(instance_loader):
        parts.append(instance_loader)

    # Mixin and LWJGL come from the instance's libraries: the mod ships mixins, and window code
    # touches SDL, so both are needed to compile rather than merely to run.
    part_libs = "D:/1.21.1F-NF/.minecraft/libraries"
    for pattern in ("com/mojang/datafixerupper/*/*.jar",
                    "com/mojang/serialization/*/*.jar",
                    "com/mojang/brigadier/*/*.jar",
                    "net/fabricmc/sponge-mixin/*/*.jar", "org/lwjgl/lwjgl/*/*.jar",
                    "org/lwjgl/lwjgl-glfw/*/*.jar", "org/lwjgl/lwjgl-sdl/*/*.jar",
                    "org/lwjgl/lwjgl-stb/*/*.jar", "org/lwjgl/lwjgl-tinyfd/*/*.jar",
                    "org/lwjgl/lwjgl-openal/*/*.jar", "org/lwjgl/lwjgl-opengl/*/*.jar"):
        import glob as _glob
        picked = [p for p in _glob.glob(os.path.join(part_libs, pattern))
                  if "natives" not in os.path.basename(p)]
        # One version is enough; several mixin releases on one classpath only invite confusion.
        if picked:
            picked.sort()
            parts.append(picked[-1])

    parts += jars("net.neoforged", "fancymodloader", "4.0.45")
    parts += jars("net.neoforged", "bus", "8.0.5")
    parts += jars("net.neoforged", "coremods", "7.0.3")
    parts += jars("net.neoforged", "mergetool")

    for group, artifact in (
            ("org.slf4j", "slf4j-api"),
            ("com.google.code.gson", "gson"),
            ("org.joml", "joml"),
            ("com.mojang", "brigadier"),
            ("org.apache.commons", "commons-lang3"),
            ("com.google.guava", "guava"),
            ("it.unimi.dsi", "fastutil"),
            ("org.jspecify", "jspecify"),
            ("org.spongepowered", "mixin"),
            ("org.ow2.asm", "asm"),
            ("org.ow2.asm", "asm-tree"),
            ("org.ow2.asm", "asm-analysis"),
            ("org.ow2.asm", "asm-commons"),
            ("org.ow2.asm", "asm-util"),
    ):
        parts += jars(group, artifact)

    return parts


def compile_java(sources, out_dir, cp, extra=()):
    """Runs javac; returns (exit code, combined diagnostics).

    Pipes are denied in this sandbox, so javac writes to files that are read back afterwards.
    """
    shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(out_dir, exist_ok=True)

    argfile = os.path.join(out_dir, "args.txt")
    lines = ["-proc:none", "-encoding", "UTF-8", "-g", "--release", "21",
             "-d", out_dir, "-classpath", cp]
    lines += list(extra)
    lines += ['"' + s.replace("\\", "/") + '"' for s in sources]
    io.open(argfile, "w", encoding="utf-8").write("\n".join(lines))

    out_txt = os.path.join(out_dir, "javac-out.txt")
    err_txt = os.path.join(out_dir, "javac-err.txt")
    with io.open(out_txt, "w", encoding="utf-8") as so, \
            io.open(err_txt, "w", encoding="utf-8") as se:
        code = subprocess.run([JAVAC, "@" + argfile], stdout=so, stderr=se).returncode

    text = ""
    for path in (err_txt, out_txt):
        if os.path.exists(path):
            text += io.open(path, encoding="utf-8", errors="replace").read()
    return code, text


def sources_under(root):
    found = []
    for directory, _dirs, files in os.walk(root):
        found += [os.path.join(directory, f) for f in files if f.endswith(".java")]
    return sorted(found)


def main():
    cp = ";".join(classpath())
    print("classpath entries:", cp.count(";") + 1)

    main_sources = sources_under(MAIN_SRC)
    print("main sources:", len(main_sources))
    code, text = compile_java(main_sources, MAIN_OUT, cp)
    if code != 0:
        print("MAIN COMPILE FAILED")
        print(text[:8000])
        return 1
    print("MAIN COMPILE OK")

    test_sources = sources_under(TEST_SRC)
    if not test_sources:
        print("no tests")
        return 0
    junit = jars("org.junit.jupiter", "junit-jupiter-api") + \
            jars("org.junit.platform", "junit-platform-commons") + \
            jars("org.opentest4j", "opentest4j") + \
            jars("org.apiguardian", "apiguardian-api")
    test_cp = cp + ";" + MAIN_OUT + ";" + ";".join(junit)
    print("test sources:", len(test_sources))
    code, text = compile_java(test_sources, TEST_OUT, test_cp)
    if code != 0:
        print("TEST COMPILE FAILED")
        print(text[:8000])
        return 1
    print("TEST COMPILE OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
