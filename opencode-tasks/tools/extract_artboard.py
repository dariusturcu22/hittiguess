"""Extracts design artboards from docs/design/hittiguess-design.html into small standalone files.

Usage (from the repo root):
    python opencode-tasks/tools/extract_artboard.py --list
    python opencode-tasks/tools/extract_artboard.py GroupLobbyDark GameSessionPlayerDark

Writes each artboard to .design-artboards/<Name>.html (git-ignored scratch folder; do not commit it).
Open the file in a browser at 1440x900 to see the design, or read its CSS for exact sizes.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, "docs", "design", "hittiguess-design.html")
OUT = os.path.join(ROOT, ".design-artboards")
ARTBOARD_SUFFIX = ".dc.html"
OUTPUT_SUFFIX = ".html"
LIST_FLAG = "--list"
DOCUMENT_PATTERN = re.compile(r'<script type="application/json" id="appifact-doc">(?P<document>.*?)</script>', re.S)


def artboards():
    text = open(SOURCE, encoding="utf8").read()
    match = DOCUMENT_PATTERN.search(text)
    return json.loads(match.group("document"))["content"]["files"]


def main(arguments):
    files = artboards()
    if not arguments or arguments == [LIST_FLAG]:
        for name in sorted(files):
            if name.endswith(ARTBOARD_SUFFIX):
                print(name.removesuffix(ARTBOARD_SUFFIX))
        return
    os.makedirs(OUT, exist_ok=True)
    for name in arguments:
        key = name if name.endswith(ARTBOARD_SUFFIX) else f"{name}{ARTBOARD_SUFFIX}"
        if key not in files:
            print(f"not found: {name}")
            continue
        path = os.path.join(OUT, key.removesuffix(ARTBOARD_SUFFIX) + OUTPUT_SUFFIX)
        open(path, "w", encoding="utf8").write(files[key])
        print(f"wrote {path} ({len(files[key])} chars)")


if __name__ == "__main__":
    main(sys.argv[1:])
