"""Validates the packs and bundles them into dist/JerryAndFriends.mcaddon.

Run from anywhere:  python3 tools/build.py
Open the resulting .mcaddon on a phone/PC and Minecraft imports both packs.
"""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKS = ROOT / "packs"
OUT = ROOT / "dist" / "JerryAndFriends.mcaddon"


def validate():
    bad = []
    for path in sorted(PACKS.rglob("*.json")):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as err:
            bad.append(f"{path.relative_to(ROOT)}: {err}")
    for pack in ("behavior_pack", "resource_pack"):
        for required in ("manifest.json", "pack_icon.png"):
            if not (PACKS / pack / required).exists():
                bad.append(f"packs/{pack}/{required} is missing")
    return bad


def build():
    OUT.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for pack in ("behavior_pack", "resource_pack"):
            for path in sorted((PACKS / pack).rglob("*")):
                if path.is_file():
                    # Fixed timestamp keeps the archive identical between builds.
                    info = zipfile.ZipInfo(f"JerryAndFriends_{pack}/{path.relative_to(PACKS / pack).as_posix()}", (2026, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    zf.writestr(info, path.read_bytes())
    print(f"Built {OUT.relative_to(ROOT)} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    problems = validate()
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    build()
