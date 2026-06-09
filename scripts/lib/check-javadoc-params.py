#!/usr/bin/env python3
"""
check-javadoc-params.py — javadoc COMPLETENESS gate for the logic packages.

The structure gate only checks javadoc PRESENCE; a mechanical `/** Does x. */`
stub passes it. This gate fails a public method/constructor whose javadoc is
missing an `@param` for any parameter, or `@return` for a non-void method.

Scope: service/reports/** and externalservices/** (skips generated + tests).
Usage:  python3 check-javadoc-params.py [BACKEND_DIR]    # default ./backend
Exit 0 clean, 1 violations, 2 bad args.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

LOGIC_PATHS = ("/service/reports/", "/externalservices/")

# javadoc block (non-greedy) → optional annotations → `public` signature up to ')'.
METHOD = re.compile(
    r"/\*\*(?P<doc>.*?)\*/\s*"
    r"(?:@\w+(?:\([^)]*\))?\s*)*"
    r"public\s+(?P<ret>(?:static\s+|final\s+|abstract\s+|synchronized\s+|<[^>]+>\s+)*"
    r"[\w.$<>,\[\]?\s]*?)\b(?P<name>\w+)\s*\((?P<params>[^)]*)\)",
    re.DOTALL,
)


def split_params(params: str) -> list[str]:
    """Top-level comma split (respects <> nesting), returns each param's name."""
    out, depth, cur = [], 0, ""
    for ch in params:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    names = []
    for p in out:
        toks = p.replace("...", " ").replace("[]", " ").split()
        toks = [t for t in toks if t not in ("final",)]
        if toks:
            names.append(toks[-1])
    return names


def scan(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    out: list[str] = []
    for m in METHOD.finditer(text):
        doc, ret, name = m.group("doc"), m.group("ret").strip(), m.group("name")
        # Skip generated-style overrides: an @Override sits between doc and sig?
        # (annotations are consumed by the regex; @Override methods may legitimately
        #  inherit docs — only flag when a javadoc IS present, which it is here.)
        params = split_params(m.group("params"))
        line = text[: m.start("name")].count("\n") + 1
        for pname in params:
            if not re.search(r"@param\s+" + re.escape(pname) + r"\b", doc):
                out.append(f"[javadoc-missing-@param] {path}:{line}: {name}(…) — no @param {pname}")
        is_ctor = ret == "" or ret == name
        is_type_decl = ret in ("record", "class", "interface", "enum")
        if not is_ctor and not is_type_decl and ret != "void" and "@return" not in doc:
            out.append(f"[javadoc-missing-@return] {path}:{line}: {name}(…) -> {ret} — no @return")
    return out


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "backend")
    if not root.is_dir():
        print(f"check-javadoc-params: not a directory: {root}", file=sys.stderr)
        return 2
    roots = [p for p in root.glob("*/src/main/java") if p.is_dir()] or [root]
    violations: list[str] = []
    for r in roots:
        for f in sorted(r.rglob("*.java")):
            t = str(f).replace("\\", "/")
            if not any(p in t for p in LOGIC_PATHS):
                continue
            if f.name.endswith(("Test.java", "Api.java")) or "/api/v1/model/" in t:
                continue
            violations.extend(scan(f))
    if violations:
        print(f"check-javadoc-params: FAIL — {len(violations)} violation(s):", file=sys.stderr)
        for v in violations:
            print(f"  {v}", file=sys.stderr)
        return 1
    print(f"check-javadoc-params: OK ({len(roots)} source tree(s) scanned)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
