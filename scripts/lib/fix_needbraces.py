#!/usr/bin/env python3
"""One-off: add braces to single-line control statements flagged by Checkstyle
NeedBraces.

Reads a checkstyle-result.xml and rewrites ONLY the flagged lines, in reverse
order so earlier line numbers stay valid. Wrapping a single controlled statement
in braces is semantically identical (`if (c) x;` == `if (c) { x; }`) and the
statement text is copied verbatim, so the worst case is a syntax error that the
compiler catches. Anything that can't be transformed cleanly is skipped and
printed for manual fixing.

Usage: python3 fix_needbraces.py <checkstyle-result.xml> [...more xmls]
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

KW = re.compile(r'^(\s*)(else\s+if|if|for|while|else)\b')


def matching_paren(s, i):
    depth = 0
    while i < len(s):
        if s[i] == '(':
            depth += 1
        elif s[i] == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def transform(line):
    body = line.rstrip('\n')
    stripped = body.lstrip()
    if stripped.startswith(('//', '*', '/*')):
        return None
    m = KW.match(body)
    if not m:
        return None
    indent, kw = m.group(1), m.group(2)
    if kw == 'else':
        head = indent + 'else'
        stmt = body[m.end():].strip()
    else:
        op = body.find('(', m.end())
        if op < 0:
            return None
        cp = matching_paren(body, op)
        if cp < 0:
            return None
        head = body[:cp + 1]
        stmt = body[cp + 1:].strip()
    # Only a single, simple, same-line statement.
    if not stmt or stmt.startswith('{') or not stmt.endswith(';'):
        return None
    if stmt.count(';') != 1 or stmt.startswith('else') or ' else ' in stmt:
        return None
    return f"{head} {{\n{indent}    {stmt}\n{indent}}}\n"


def main():
    flagged = defaultdict(set)
    for res in sys.argv[1:]:
        for f in ET.parse(res).getroot().findall('file'):
            name = f.get('name')
            for e in f.findall('error'):
                if e.get('source', '').endswith('NeedBracesCheck'):
                    flagged[name].add(int(e.get('line')))
    fixed = skipped = 0
    for path, lines in flagged.items():
        with open(path, encoding='utf-8') as fh:
            content = fh.readlines()
        for ln in sorted(lines, reverse=True):
            idx = ln - 1
            if idx >= len(content):
                continue
            new = transform(content[idx])
            if new is None:
                print(f"SKIP {path}:{ln}: {content[idx].strip()}")
                skipped += 1
                continue
            content[idx] = new
            fixed += 1
        with open(path, 'w', encoding='utf-8') as fh:
            fh.writelines(content)
    print(f"NeedBraces: fixed={fixed} skipped={skipped}")


if __name__ == '__main__':
    main()
