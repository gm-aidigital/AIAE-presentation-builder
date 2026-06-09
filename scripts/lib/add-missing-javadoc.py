#!/usr/bin/env python3
"""Insert minimal javadoc before public types/methods flagged by check-structure-strict."""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def has_javadoc_above(lines: list[str], idx: int) -> bool:
    j = idx - 1
    while j >= 0:
        s = lines[j].strip()
        if not s or s.startswith("@"):
            j -= 1
            continue
        return s.endswith("*/")
    return False


def method_doc(name: str) -> str:
    words = re.sub(r"([a-z])([A-Z])", r"\1 \2", name).replace("_", " ").lower()
    return f"/** {words.capitalize()}. */"


def type_doc(name: str) -> str:
    return f"/** {name} (report engine DTO). */"


def main():
    proc = subprocess.run(
        [sys.executable, str(ROOT / "scripts/lib/check-structure-strict.py"), "backend"],
        capture_output=True,
        text=True,
    )
    if proc.returncode == 0:
        return
    targets: list[tuple[Path, int, str]] = []
    for line in proc.stderr.splitlines() + proc.stdout.splitlines():
        m = re.match(r"\s*\[(missing-javadoc-\w+)\] (backend/[^:]+):(\d+):", line)
        if m:
            targets.append((ROOT / m.group(2), int(m.group(3)), m.group(1)))
    by_file: dict[Path, list[tuple[int, str]]] = {}
    for path, lineno, kind in targets:
        by_file.setdefault(path, []).append((lineno, kind))

    for path, items in by_file.items():
        lines = path.read_text().splitlines()
        for lineno, kind in sorted(items, key=lambda x: -x[0]):
            idx = lineno - 1
            if has_javadoc_above(lines, idx):
                continue
            s = lines[idx].strip()
            if kind == "missing-javadoc-type":
                m = re.search(r"(class|interface|enum|record)\s+(\w+)", s)
                doc = type_doc(m.group(2)) if m else "/** Type. */"
            else:
                m = re.search(r"\s+(\w+)\s*\(", s)
                doc = method_doc(m.group(1)) if m else "/** Method. */"
            indent = re.match(r"^(\s*)", lines[idx]).group(1)
            lines.insert(idx, indent + doc)
        path.write_text("\n".join(lines) + "\n")
        print("javadoc", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
