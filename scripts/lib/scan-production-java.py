#!/usr/bin/env python3
"""Production Java scanners for magic literals and static methods."""

from __future__ import annotations

import re
import sys
from pathlib import Path

SKIP_SUFFIXES = (
    "/api/v1/model/",
    "/api/v1/invoker/",
)
# POC-harvested report engine — verbatim static helpers (Fmt, Claude normalizers).
HARVEST_LEGACY_PATHS = (
    "/service/reports/",
    "/externalservices/",
)
SKIP_FILES = {
    "AuthConstants.java",
}

CONST_DECL = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|protected|private)\s+)?"
    r"(?:static\s+)?(?:final\s+)?"
    r"[\w.<>,\s\[\]?]+\s+\w+\s*="
)
STATIC_FINAL = re.compile(r"^\s*(?:(?:public|protected|private)\s+)?static\s+final\b")
METHOD_SIG = re.compile(
    r"^\s*(?:(?:public|protected|private)\s+)?"
    r"(?:static\s+)?"
    r"(?!class\b|interface\b|enum\b|record\b)"
    r"[\w.<>,\s\[\]?]+\s+(\w+)\s*\([^;]*\)\s*(?:throws\s+[\w.,\s]+)?\s*\{?\s*$"
)
STATIC_METHOD = re.compile(
    r"^\s*(?:(?:public|protected|private)\s+)?"
    r"static\s+(?!final\b)(?!class\b|interface\b|enum\b|record\b)"
    r"[^=;{]+?\([^)]*\)"
)

MAGIC_RULES = [
    ("jwt-claim-name", re.compile(r'"(?:user_id|full_name|azp)"')),
    ("cors-max-age", re.compile(r"maxAge\s*\(\s*\d{3,}\s*\)")),
    (
        "hardcoded-actuator-path",
        re.compile(
            r'"/actuator/health"|"/actuator/prometheus"|"/swagger-ui|"/v3/api-docs|"/api-docs'
        ),
    ),
    (
        "usage-logging-pool-magic",
        re.compile(
            r"new\s+LinkedBlockingQueue\s*\(\s*\d+\s*\)"
            r"|corePoolSize\s*=\s*\d+"
            r"|maximumPoolSize\s*=\s*\d+"
        ),
    ),
]


def should_skip_file(path: Path) -> bool:
    text = str(path).replace("\\", "/")
    if path.name in SKIP_FILES:
        return True
    if path.name.endswith("Api.java"):
        return True
    if any(part in text for part in SKIP_SUFFIXES):
        return True
    return any(part in text for part in HARVEST_LEGACY_PATHS)


def strip_comments_and_strings(line: str) -> str:
    """Remove // comments and string literals for safer matching."""
    if "//" in line:
        line = line.split("//", 1)[0]
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', line)


def in_method_body(lines: list[str], index: int) -> bool:
    depth = 0
    body_threshold: int | None = None
    pending_method = False
    for raw in lines[: index + 1]:
        if CONST_DECL.match(raw) or STATIC_FINAL.match(raw):
            depth += raw.count("{") - raw.count("}")
            pending_method = False
            continue
        if METHOD_SIG.match(raw):
            if "{" in raw:
                body_threshold = depth + raw.count("{")
                pending_method = False
            else:
                pending_method = True
        elif pending_method and "{" in raw:
            body_threshold = depth + raw.count("{")
            pending_method = False
        depth += raw.count("{") - raw.count("}")
    if body_threshold is None:
        return False
    return depth >= body_threshold


def scan_magic(path: Path) -> list[str]:
    violations: list[str] = []
    lines = path.read_text(encoding="utf-8").splitlines()
    for i, raw in enumerate(lines):
        stripped = raw.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
            continue
        if stripped.startswith("@"):
            continue
        if CONST_DECL.match(raw) or STATIC_FINAL.match(raw):
            continue
        if not in_method_body(lines, i):
            continue
        for name, pattern in MAGIC_RULES:
            if pattern.search(raw):
                violations.append(f"[{name}] {path}:{i + 1}: {raw.strip()[:120]}")
                break
    return violations


def scan_static(path: Path) -> list[str]:
    violations: list[str] = []
    for i, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = raw.strip()
        if not stripped or stripped.startswith("//") or stripped.startswith("/*") or stripped.startswith("*"):
            continue
        if "@Bean" in raw:
            continue
        if STATIC_FINAL.match(raw):
            continue
        if re.search(r"static\s+final\s+(?:Logger|org\.slf4j\.Logger)\b", raw):
            continue
        if re.search(r"static\s+(?:class|interface|enum|record)\b", raw):
            continue
        if re.match(r"^\s*static\s*\{", raw):
            continue
        if not STATIC_METHOD.search(raw):
            continue
        if "void main(" in raw:
            continue
        violations.append(f"{path}:{i}: {stripped[:120]}")
    return violations


def collect_dirs(args: list[str]) -> list[Path]:
    if args:
        return [Path(p) for p in args]
    roots = []
    for part in Path("backend").glob("*/src/main/java"):
        if part.is_dir():
            roots.append(part)
    return roots


def main() -> int:
    if len(sys.argv) < 2 or sys.argv[1] not in {"magic", "static"}:
        print("Usage: scan-production-java.py <magic|static> [src_root...]", file=sys.stderr)
        return 2

    mode = sys.argv[1]
    dirs = collect_dirs(sys.argv[2:])
    if not dirs:
        print(f"scan-production-java ({mode}): no source directories", file=sys.stderr)
        return 0

    violations: list[str] = []
    for root in dirs:
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("*.java")):
            if should_skip_file(path):
                continue
            if mode == "magic":
                violations.extend(scan_magic(path))
            else:
                violations.extend(scan_static(path))

    label = "check-production-magic-values" if mode == "magic" else "check-production-static-methods"
    if violations:
        print(f"{label}: FAIL — {len(violations)} violation(s):", file=sys.stderr)
        for v in violations:
            print(f"  {v}", file=sys.stderr)
        return 1

    print(f"{label}: OK ({len(dirs)} source tree(s) scanned)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
