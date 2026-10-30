"""Checks that every @Inject target named in the mixin configs actually exists.

This exists because one bad target is enough to crash the game while the world loads, and that only
happens when the target class is first touched — a mixin on `MultiPlayerGameMode` therefore looks
fine on the title screen and kills the game on world join. Verifying all targets offline is much
cheaper than discovering them one crash at a time.
"""

import io
import os
import re
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PROJECT = r"D:\@临时工程\更多开关功能"
JAR = os.path.join(PROJECT, "_build", "nf-mc", "mc-1.21.1-named.jar")
NEORGE = os.path.join(PROJECT, "_build", "gradle-home")
JAVAP = r"C:\Users\BZX\Documents\deepseek-harness\default-workspace\mcmod\tools\jdk25\bin\javap.exe"
MIXIN_DIR = os.path.join(PROJECT, "src", "main", "java", "com", "heishandashu", "moreswitches")


def mixin_targets():
    """Maps each mixin class to the class it targets, read from the @Mixin annotation."""
    found = {}
    for root, _dirs, files in os.walk(MIXIN_DIR):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(root, name)
            text = io.open(path, encoding="utf-8").read()
            match = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?([A-Za-z0-9_.]+)\.class", text)
            if not match:
                continue
            found[name[:-5]] = match.group(1)
    return found


def inject_targets():
    """Collects (handler name, target method, target class) for every @Inject."""
    out = []
    for root, _dirs, files in os.walk(MIXIN_DIR):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(root, name)
            text = io.open(path, encoding="utf-8").read()
            mixin = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?([A-Za-z0-9_.]+)\.class", text)
            if not mixin:
                continue
            cls = mixin.group(1)
            # method = "..." may span several lines with + concatenation.
            for block in re.finditer(r"@Inject\((.*?)\)\s*\n\s*private[^\(]*\(", text, re.S):
                body = block.group(1)
                m = re.search(r'method\s*=\s*("(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*)', body, re.S)
                if not m:
                    continue
                literal = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1)))
                out.append((cls, literal, path))
    return out


def resolve_class(mixin_file, simple):
    """Finds the fully-qualified name of a target class from the mixin's import statements."""
    text = io.open(mixin_file, encoding="utf-8").read()
    for match in re.finditer(r"^import\s+([A-Za-z0-9_.]+)\.([A-Za-z0-9_]+);", text, re.M):
        if match.group(2) == simple:
            return match.group(1) + "." + match.group(2)
    return simple


def methods_of(class_name):
    """Every method name javap reports for one class, lowercased name only."""
    simple = class_name.replace("/", ".")
    # Pipes are denied in this sandbox, so javap writes to a file that is read back afterwards.
    dump = os.path.join(PROJECT, "_build", "javap-dump.txt")
    with io.open(dump, "w", encoding="utf-8") as handle:
        subprocess.run([JAVAP, "-p", "-classpath", JAR, simple], stdout=handle, stderr=handle)
    text = io.open(dump, encoding="utf-8", errors="replace").read()
    names = set()
    for line in text.splitlines():
        line = line.strip()
        if "(" not in line or not line.endswith(";"):
            continue
        head = line.split("(")[0]
        names.add(head.split()[-1])
    return names


def main():
    if not os.path.exists(JAR):
        print("missing game jar:", JAR)
        return 1

    targets = inject_targets()
    print("injections found:", len(targets))

    cache = {}
    problems = []
    for cls, literal, path in targets:
        # The literal may carry a full descriptor; only the name matters here.
        method = literal.split("(")[0]
        full = resolve_class(path, cls)
        if full not in cache:
            cache[full] = methods_of(full)
        cls_available = cache[full]
        available = cache[full]
        if not available:
            problems.append(f"{full}: class not found by javap")
            continue
        # A descriptor-qualified target is still just a name for this check.
        if method not in available:
            problems.append(f"{full}: no method '{method}'")
        else:
            print(f"  ok  {full}.{method}")

    if problems:
        print("\nBROKEN TARGETS:")
        for problem in problems:
            print("  " + problem)
        return 1
    print("\nall mixin targets exist")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
