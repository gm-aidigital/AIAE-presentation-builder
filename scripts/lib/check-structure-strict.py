#!/usr/bin/env python3
"""
check-structure-strict.py — enforce the org structure rules the POC harvest violates.

Three rules, NO per-feature path exemptions (suppressing a gate by skipping a
directory is itself a violation — that is how the harvested code "passed"):

  1. NO static methods in production code (only `static final` constants + @Bean
     factory methods + `public static void main` are allowed). Logic belongs on
     injectable instance beans so it is mockable/unit-testable.
  2. NO private methods/constructors in the logic packages (service/reports,
     externalservices). A private method is untestable logic and a private
     constructor is the static-utility-class anti-pattern — convert the class to
     an injectable @Component and make the helper a public instance method.
  3. Javadoc REQUIRED on every public type and public method in the logic
     packages (skips @Override, which inherits its contract).

Usage:
  python3 check-structure-strict.py [BACKEND_DIR]      # default: ./backend
Exit 0 = clean, 1 = violations, 2 = bad args.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

# Logic packages where rules 2 + 3 (private + javadoc) apply. Rule 1 (static)
# applies EVERYWHERE in production main — there is intentionally no allow-list of
# feature directories.
LOGIC_PATHS = ("/service/reports/", "/externalservices/")

# Always skipped: generated code and tests (not hand-written production logic).
def is_skipped(path: Path) -> bool:
    t = str(path).replace("\\", "/")
    if t.endswith(("Test.java", "Tests.java", "package-info.java")):
        return True
    return "/api/v1/model/" in t or "/api/v1/invoker/" in t or path.name.endswith("Api.java")

def in_logic(path: Path) -> bool:
    t = str(path).replace("\\", "/")
    return any(p in t for p in LOGIC_PATHS)

STATIC_FINAL = re.compile(r"^\s*(?:(?:public|protected|private)\s+)?static\s+final\b")
STATIC_METHOD = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|protected|private)\s+)?static\s+"
    r"(?!final\b|class\b|interface\b|enum\b|record\b|\{)"
    r"[^=;]*?\([^)]*\)"
)
# A private method (has a return type) OR a private constructor (no return type).
PRIVATE_METHOD = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*private\s+(?:static\s+)?(?:final\s+)?"
    r"(?!class\b|interface\b|enum\b|record\b)"
    r"[\w.<>,\[\]?]+\s+\w+\s*\([^;=]*\)\s*(?:throws[\w.,\s]+)?\{?\s*$"
)
PRIVATE_CTOR = re.compile(r"^\s*private\s+([A-Z]\w*)\s*\([^;=]*\)\s*\{?\s*$")
PUBLIC_METHOD = re.compile(
    r"^\s*public\s+(?:static\s+)?(?:final\s+)?"
    r"(?!class\b|interface\b|enum\b|record\b)"
    r"[\w.<>,\[\]?]+\s+(\w+)\s*\([^;=]*\)\s*(?:throws[\w.,\s]+)?\{?\s*$"
)
PUBLIC_TYPE = re.compile(r"^\s*public\s+(?:final\s+|abstract\s+)?(class|interface|enum|record)\s+(\w+)")


def has_javadoc_above(lines: list[str], idx: int) -> bool:
    """True if a javadoc block close (*/) sits just above, through any annotations."""
    j = idx - 1
    while j >= 0:
        s = lines[j].strip()
        if not s or s.startswith("@"):
            j -= 1
            continue
        return s.endswith("*/")
    return False


def scan(path: Path) -> list[str]:
    out: list[str] = []
    lines = path.read_text(encoding="utf-8").splitlines()
    logic = in_logic(path)
    prev_was_override = False
    for i, raw in enumerate(lines):
        s = raw.strip()
        annotated_override = s == "@Override" or s.startswith("@Override")
        if not s or s.startswith(("//", "/*", "*")):
            continue
        # Rule 1 — static methods (everywhere)
        if (STATIC_METHOD.search(raw) and not STATIC_FINAL.match(raw)
                and "@Bean" not in raw and "void main(" not in raw
                and not re.search(r"static\s+(?:class|interface|enum|record)\b", raw)
                and not re.search(r"static\s+final\s+(?:Logger|org\.slf4j\.Logger)\b", raw)):
            out.append(f"[static-method] {path}:{i+1}: {s[:110]}")
        if logic:
            # Rule 2 — private methods / constructors
            if PRIVATE_METHOD.match(raw) or PRIVATE_CTOR.match(raw):
                kind = "private-constructor (static-utility class → make it an injectable @Component)" \
                    if PRIVATE_CTOR.match(raw) else "private-method (extract to a public instance method on a bean)"
                out.append(f"[{kind.split()[0]}] {path}:{i+1}: {s[:90]}  <- {kind}")
            # Rule 3 — javadoc on public type/method (skip @Override)
            m_pub = PUBLIC_METHOD.match(raw)
            # Trivial accessors (get*/set*/is*) on POJOs/props beans don't need javadoc.
            is_accessor = bool(m_pub and re.match(r"(?:get|set|is)[A-Z]", m_pub.group(1)))
            if PUBLIC_TYPE.match(raw) and not has_javadoc_above(lines, i):
                out.append(f"[missing-javadoc-type] {path}:{i+1}: {s[:90]}")
            elif m_pub and not is_accessor and not prev_was_override and not has_javadoc_above(lines, i):
                out.append(f"[missing-javadoc-method] {path}:{i+1}: {s[:90]}")
        prev_was_override = annotated_override
    return out


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "backend")
    if not root.is_dir():
        print(f"check-structure-strict: not a directory: {root}", file=sys.stderr)
        return 2
    roots = [p for p in root.glob("*/src/main/java") if p.is_dir()] or [root]
    violations: list[str] = []
    for r in roots:
        for f in sorted(r.rglob("*.java")):
            if not is_skipped(f):
                violations.extend(scan(f))
    by_rule: dict[str, int] = {}
    for v in violations:
        by_rule[v.split("]")[0].strip("[")] = by_rule.get(v.split("]")[0].strip("["), 0) + 1
    if violations:
        print(f"check-structure-strict: FAIL — {len(violations)} violation(s) {by_rule}:", file=sys.stderr)
        for v in violations:
            print(f"  {v}", file=sys.stderr)
        return 1
    print(f"check-structure-strict: OK ({len(roots)} source tree(s) scanned)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
