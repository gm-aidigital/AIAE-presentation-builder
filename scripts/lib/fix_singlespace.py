#!/usr/bin/env python3
"""One-off: collapse multi-space token separators flagged by Checkstyle
SingleSpaceSeparator (column-alignment spacing between literals).

Operates only on the flagged lines. Preserves leading indentation and the
contents of string/char literals; collapses runs of 2+ spaces to one only in
code positions. Whitespace inside string literals is part of the string token
and is never flagged, so it is left untouched.

Usage: python3 fix_singlespace.py <checkstyle-result.xml> [...]
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict


def collapse(line):
    nl = '\n' if line.endswith('\n') else ''
    line = line.rstrip('\n')
    indent = re.match(r'^(\s*)', line).group(1)
    rest = line[len(indent):]
    out = []
    i = 0
    in_str = in_char = False
    while i < len(rest):
        c = rest[i]
        if in_str:
            out.append(c)
            if c == '\\' and i + 1 < len(rest):
                out.append(rest[i + 1])
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if in_char:
            out.append(c)
            if c == '\\' and i + 1 < len(rest):
                out.append(rest[i + 1])
                i += 2
                continue
            if c == "'":
                in_char = False
            i += 1
            continue
        if c == '"':
            in_str = True
            out.append(c)
            i += 1
            continue
        if c == "'":
            in_char = True
            out.append(c)
            i += 1
            continue
        if c == ' ' and i + 1 < len(rest) and rest[i + 1] == ' ':
            out.append(' ')
            while i < len(rest) and rest[i] == ' ':
                i += 1
            continue
        out.append(c)
        i += 1
    return indent + ''.join(out) + nl


def main():
    flagged = defaultdict(set)
    for res in sys.argv[1:]:
        for f in ET.parse(res).getroot().findall('file'):
            name = f.get('name')
            for e in f.findall('error'):
                if e.get('source', '').endswith('SingleSpaceSeparatorCheck'):
                    flagged[name].add(int(e.get('line')))
    fixed = 0
    for path, lines in flagged.items():
        with open(path, encoding='utf-8') as fh:
            content = fh.readlines()
        for ln in lines:
            idx = ln - 1
            if idx < len(content):
                new = collapse(content[idx])
                if new != content[idx]:
                    content[idx] = new
                    fixed += 1
        with open(path, 'w', encoding='utf-8') as fh:
            fh.writelines(content)
    print(f"SingleSpaceSeparator: fixed={fixed}")


if __name__ == '__main__':
    main()
