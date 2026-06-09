#!/usr/bin/env python3
"""One-off: fix the remaining cleanly-mechanical Checkstyle rules on flagged lines.

  * MultipleVariableDeclarations: split `int a = 1, b = 2;` into one statement
    per line. Only simple types (no generic `<...>` / array commas in the type)
    are handled; anything else is skipped for manual fixing.
  * WhitespaceAround (empty block): `{}` -> `{ }` on the flagged line.

Conservative: edits only Checkstyle-flagged lines; skips and prints anything it
can't transform confidently. Verify with compile + tests afterwards.

Usage: python3 fix_mechanical.py <checkstyle-result.xml> [...]
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

MVD = re.compile(r'^(\s*)((?:public|private|protected|static|final|\s)*)([A-Za-z_][\w.]*)\s+(.+);\s*$')


def split_top_commas(s):
    parts, depth, buf = [], 0, []
    for c in s:
        if c in '([<{':
            depth += 1
        elif c in ')]>}':
            depth -= 1
        if c == ',' and depth == 0:
            parts.append(''.join(buf))
            buf = []
        else:
            buf.append(c)
    parts.append(''.join(buf))
    return [p.strip() for p in parts]


def split_var_decl(line):
    body = line.rstrip('\n')
    m = MVD.match(body)
    if not m:
        return None
    indent, mods, typ, decls = m.group(1), m.group(2), m.group(3), m.group(4)
    if '<' in typ or '[' in typ or '<' in mods:
        return None
    parts = split_top_commas(decls)
    if len(parts) < 2:
        return None
    prefix = (mods + ' ' if mods.strip() else '') + typ
    return ''.join(f"{indent}{prefix} {p};\n" for p in parts)


def main():
    mvd = defaultdict(set)
    wsa = defaultdict(set)
    for res in sys.argv[1:]:
        for f in ET.parse(res).getroot().findall('file'):
            name = f.get('name')
            for e in f.findall('error'):
                src = e.get('source', '')
                if src.endswith('MultipleVariableDeclarationsCheck'):
                    mvd[name].add(int(e.get('line')))
                elif src.endswith('WhitespaceAroundCheck'):
                    wsa[name].add(int(e.get('line')))
    fixed_mvd = fixed_wsa = skipped = 0
    files = set(mvd) | set(wsa)
    for path in files:
        with open(path, encoding='utf-8') as fh:
            content = fh.readlines()
        # WhitespaceAround empty braces (does not change line count)
        for ln in wsa.get(path, ()):
            idx = ln - 1
            if idx < len(content) and '{}' in content[idx]:
                content[idx] = content[idx].replace('{}', '{ }')
                fixed_wsa += 1
        # MultipleVariableDeclarations (reverse order; changes line count)
        for ln in sorted(mvd.get(path, ()), reverse=True):
            idx = ln - 1
            if idx >= len(content):
                continue
            new = split_var_decl(content[idx])
            if new is None:
                print(f"SKIP MVD {path}:{ln}: {content[idx].strip()}")
                skipped += 1
                continue
            content[idx] = new
            fixed_mvd += 1
        with open(path, 'w', encoding='utf-8') as fh:
            fh.writelines(content)
    print(f"MultipleVarDecl fixed={fixed_mvd}  WhitespaceAround(empty-brace) fixed={fixed_wsa}  skipped={skipped}")


if __name__ == '__main__':
    main()
