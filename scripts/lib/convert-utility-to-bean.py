#!/usr/bin/env python3
"""One-off: convert static utility class to @Component bean (instance methods)."""
import re
import sys
from pathlib import Path

def convert(content: str) -> str:
    if "@Component" in content:
        return content
    content = re.sub(
        r"public final class (\w+)",
        r"@Component\npublic class \1",
        content,
        count=1,
    )
    if "import org.springframework.stereotype.Component;" not in content:
        content = content.replace(
            "package ",
            "package ",
        )
        pkg_end = content.index("\n", content.index("package "))
        content = (
            content[: pkg_end + 1]
            + "\nimport org.springframework.stereotype.Component;\n"
            + content[pkg_end + 1 :]
        )
    content = re.sub(r"\n\s*private\s+\w+\(\)\s*\{\s*\}\s*\n", "\n", content)
    lines = content.splitlines(keepends=True)
    out = []
    for line in lines:
        if STATIC_FINAL.match(line) or re.search(r"^\s*static\s*\{", line):
            out.append(line)
            continue
        if re.search(r"^\s*private\s+static\s+final\s+class\s+", line):
            out.append(line)
            continue
        # private static method -> package-private instance
        m = re.match(
            r"^(\s*)private\s+static\s+((?:final\s+)?[\w.<>,\[\]?]+\s+\w+\s*\([^)]*\)\s*(?:throws[\w.,\s]+)?\{?\s*)$",
            line,
        )
        if m:
            out.append(m.group(1) + m.group(2) + "\n")
            continue
        # public static method -> public instance
        m = re.match(
            r"^(\s*)public\s+static\s+((?:final\s+)?[\w.<>,\[\]?]+\s+\w+\s*\([^)]*\)\s*(?:throws[\w.,\s]+)?\{?\s*)$",
            line,
        )
        if m:
            out.append(m.group(1) + "public " + m.group(2).lstrip() + "\n")
            continue
        out.append(line)
    return "".join(out)

STATIC_FINAL = re.compile(r"^\s*(?:(?:public|protected|private)\s+)?static\s+final\b")

if __name__ == "__main__":
    for p in sys.argv[1:]:
        path = Path(p)
        text = path.read_text()
        path.write_text(convert(text))
        print("converted", path)
