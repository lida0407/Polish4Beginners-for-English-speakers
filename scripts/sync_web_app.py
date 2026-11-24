#!/usr/bin/env python3
"""Refresh what the web app (docs/app) takes from the Android app.

    python3 scripts/sync_web_app.py            # refresh data, fonts, version stamp and build id
    python3 scripts/sync_web_app.py --check    # fail if the web app lags behind the Android app

Copies the lesson data the web app does not already get from docs/ (the grammar
guide, the 23 grammar lessons, the alphabet), subsets the three font families
to WOFF2 with full Polish, and stamps a build id so the service worker refetches.
The phrase deck and conversations are read from docs/phrases.json and
docs/dialogs.json, the same files the Android app updates from.
"""
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "polish-phrasebook-android/app/src/main/assets"
FONTS = ROOT / "polish-phrasebook-android/app/src/main/res/font"
APP = ROOT / "docs/app"
UNICODES = "U+0020-007E,U+00A0-017F,U+2010-2027,U+2030-203A,U+20AC,U+2190-2193,U+2022,U+2026,U+2605,U+2606,U+2713,U+2717,U+25B6,U+25B8,U+25BE"
FACES = {
    "nunito_regular": "nunito-400", "nunito_bold": "nunito-700", "nunito_extrabold": "nunito-800",
    "nunito_black": "nunito-900", "baloo2_semibold": "baloo2-600", "baloo2_bold": "baloo2-700",
    "baloo2_extrabold": "baloo2-800", "literata_regular": "literata-400", "literata_bold": "literata-700",
    "literata_display": "literata-650",
}


def main():
    check = "--check" in sys.argv
    stale = []
    for name in ("grammar_guide.json", "grammar_lessons.json", "alphabet.json"):
        if check:
            if not (APP / "data" / name).exists() or (ASSETS / name).read_bytes() != (APP / "data" / name).read_bytes():
                stale.append(f"docs/app/data/{name}")
        else:
            shutil.copyfile(ASSETS / name, APP / "data" / name)
    for name in ("phrases.json", "dialogs.json"):
        if (ASSETS / name).read_bytes() != (ROOT / "docs" / name).read_bytes():
            stale.append(f"docs/{name}")
    if check:
        # CI / pre-release guard: the web app and the Android app must ship the same lessons and version.
        gradle_text = next(p for p in (ROOT / "polish-phrasebook-android/app").glob("build.gradle*")).read_text(encoding="utf-8")
        want = re.search(r'versionName\s*=?\s*"([^"]+)"', gradle_text).group(1)
        have = json.loads((APP / "build.json").read_text(encoding="utf-8")).get("androidVersion") if (APP / "build.json").exists() else None
        if have != want:
            stale.append(f"web app is stamped {have}, Android is {want}")
        if stale:
            sys.exit("OUT OF SYNC, run scripts/sync_web_app.py:\n  " + "\n  ".join(stale))
        print("web app and Android app are in sync:", want)
        return
    for item in stale:
        print(f"WARNING: {item} differs from the bundled Android copy")
    for src, out in FACES.items():
        dest = APP / "fonts" / f"{out}.woff2"
        if dest.exists() and dest.stat().st_mtime >= (FONTS / f"{src}.ttf").stat().st_mtime:
            continue
        subprocess.run([sys.executable, "-m", "fontTools.subset", str(FONTS / f"{src}.ttf"),
                        f"--unicodes={UNICODES}", "--flavor=woff2", "--layout-features=kern,liga,locl",
                        f"--output-file={dest}"], check=True)
    gradle = (ROOT / "polish-phrasebook-android/app/build.gradle.kts")
    if not gradle.exists():
        gradle = ROOT / "polish-phrasebook-android/app/build.gradle"
    version = re.search(r'versionName\s*=?\s*"([^"]+)"', gradle.read_text(encoding="utf-8")).group(1)
    data_version = json.loads((ROOT / "docs/database.json").read_text(encoding="utf-8"))["dataVersion"]
    # One version number for both: the web app reports the Android versionName it was built from.
    core = APP / "js" / "core.js"
    text = core.read_text(encoding="utf-8")
    stamped = re.sub(r"export const APP_VERSION = '[^']*';", f"export const APP_VERSION = '{version}';", text)
    if stamped != text:
        core.write_text(stamped, encoding="utf-8")
    digest = hashlib.sha256()
    files = sorted(p for p in APP.rglob("*") if p.is_file() and p.name not in ("build.json",) and ".DS_Store" not in p.name)
    for p in files:
        digest.update(p.relative_to(APP).as_posix().encode())
        digest.update(p.read_bytes())
    for name in ("phrases.json", "dialogs.json"):
        digest.update((ROOT / "docs" / name).read_bytes())
    build = {"build": digest.hexdigest()[:12], "androidVersion": version, "dataVersion": data_version,
             "files": [p.relative_to(APP).as_posix() for p in files if not p.name.endswith(".test.mjs")]}
    (APP / "build.json").write_text(json.dumps(build, indent=1), encoding="utf-8")
    print("web app build", build["build"], len(build["files"]), "files")


if __name__ == "__main__":
    main()
