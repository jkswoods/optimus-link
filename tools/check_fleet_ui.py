#!/usr/bin/env python3
"""Check a reproduced native CustomUI identifier constraint before packaging."""
from pathlib import Path
import re,posixpath
ROOT=Path(__file__).resolve().parents[1]
for p in (ROOT/'src/main/resources/Common/UI/Custom/Pages/OptimusLink').glob('*.ui'):
 source=p.read_text();text=re.sub(r'"(?:\\.|[^"\\])*"','""',source)
 bad=re.search(r'[#@]([A-Za-z][A-Za-z0-9]*_[A-Za-z0-9_]*)',text)
 if bad:raise SystemExit(f'{p.name}: native UI identifiers must be alphanumeric: {bad.group(1)}')
 for texture in re.findall(r'TexturePath:\s*"([^\"]+)"',source):
  relative=p.parent.relative_to(ROOT/'src/main/resources/Common/UI/Custom').as_posix()
  resolved=posixpath.normpath(relative+'/'+texture)
  if resolved=='..' or resolved.startswith('../'):
   raise SystemExit(f'{p.name}: texture escapes the native CustomUI asset root: {texture}')
print('Fleet CustomUI identifier syntax: PASS')
