#!/usr/bin/env python3
"""Bulk-fix private/static methods and stray public static in logic packages."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "backend"
LOGIC = ("/service/reports/", "/externalservices/")


def in_logic(p: Path) -> bool:
    t = str(p).replace("\\", "/")
    return any(x in t for x in LOGIC)


def demote_private_methods(text: str) -> str:
    lines = text.splitlines(keepends=True)
    out = []
    for line in lines:
        if re.match(r"^\s*private\s+static\s+final\b", line):
            out.append(line)
            continue
        if re.match(r"^\s*private\s+static\s+class\b", line):
            out.append(line)
            continue
        m = re.match(
            r"^(\s*)private\s+static\s+((?:final\s+)?[\w.<>,\[\]?]+\s+\w+\s*\([^)]*\)\s*(?:throws[\w.,\s]+)?\{?\s*)$",
            line,
        )
        if m:
            out.append(m.group(1) + m.group(2) + "\n")
            continue
        m = re.match(
            r"^(\s*)private\s+((?:static\s+)?(?:final\s+)?[\w.<>,\[\]?]+\s+\w+\s*\([^)]*\)\s*(?:throws[\w.,\s]+)?\{?\s*)$",
            line,
        )
        if m and "class " not in m.group(2):
            g2 = m.group(2).replace("static ", "", 1)
            out.append(m.group(1) + g2 + "\n")
            continue
        out.append(line)
    return "".join(out)


def fix_public_static_in_components(text: str) -> str:
    if "@Component" not in text and "@Service" not in text:
        return text
    return re.sub(
        r"(\n\s*)public\s+static\s+((?:final\s+)?[\w.<>,\[\]?]+\s+\w+\s*\()",
        r"\1public \2",
        text,
    )


def main():
    for path in ROOT.rglob("*.java"):
        if not in_logic(path) or path.name.endswith("Test.java"):
            continue
        text = path.read_text()
        orig = text
        text = demote_private_methods(text)
        text = fix_public_static_in_components(text)
        if text != orig:
            path.write_text(text)
            print("fixed", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
