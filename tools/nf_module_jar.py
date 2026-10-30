"""Turns the deobfuscated Minecraft jar into a named module.

NeoForge's launch handler looks up a module called ``minecraft`` in the module layer and fails with
``NoSuchElementException`` when it is absent. The official launcher gets one because it merges the
game jar with ``--module-path`` and the merged jar carries a module descriptor. The deobfuscated jar
built here has neither, so this adds an ``Automatic-Module-Name`` entry: that is enough for the JVM
to treat the jar as a named module on the module path, without touching a single class.
"""

import io
import os
import re
import shutil
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PROJECT = r"D:\@临时工程\更多开关功能"
SRC = os.path.join(PROJECT, "_build", "nf-mc", "mc-1.21.1-named.jar")
DST = os.path.join(PROJECT, "_build", "nf-mc", "minecraft-module.jar")

MODULE_NAME = "minecraft"


def main():
    if not os.path.exists(SRC):
        print("missing deobfuscated jar:", SRC)
        return 1

    with zipfile.ZipFile(SRC) as source:
        names = source.namelist()
        if "module-info.class" in names:
            # Already a named module; a straight copy is enough.
            shutil.copyfile(SRC, DST)
            print("jar already has a module descriptor; copied")
            print("out:", DST, os.path.getsize(DST))
            return 0
        manifest = source.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")

    # Drop any signature files: adding to the manifest invalidates them, and the JVM would refuse to
    # load a jar whose digests no longer match.
    drop = re.compile(r"^(META-INF/[^/]*\.(SF|DSA|RSA|EC))$", re.IGNORECASE)

    with zipfile.ZipFile(SRC) as source, \
            zipfile.ZipFile(DST, "w", zipfile.ZIP_DEFLATED) as target:
        for item in source.infolist():
            if drop.match(item.filename):
                continue
            if item.filename == "META-INF/MANIFEST.MF":
                continue
            target.writestr(item, source.read(item.filename))

        lines = manifest.replace("\r\n", "\n").split("\n")
        # Cut the digest entries and any previous automatic name, keep everything else.
        kept = []
        for line in lines:
            if line.startswith(" ") and kept and kept[-1].lower().startswith(("sha-", "name:")):
                continue
            if re.match(r"^(SHA-\d+|MD5)-Digest", line, re.IGNORECASE):
                continue
            if line.lower().startswith("automatic-module-name"):
                continue
            if not line.strip():
                continue
            kept.append(line)
        if not any(k.lower().startswith("manifest-version") for k in kept):
            kept.insert(0, "Manifest-Version: 1.0")
        kept.append("Automatic-Module-Name: " + MODULE_NAME)
        target.writestr("META-INF/MANIFEST.MF", "\r\n".join(kept) + "\r\n")

    print("module jar:", DST)
    print("size:", os.path.getsize(DST))
    with zipfile.ZipFile(DST) as check:
        has = "META-INF/MANIFEST.MF" in check.namelist()
        body = check.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") if has else ""
        print("automatic name present:", "Automatic-Module-Name: minecraft" in body)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
