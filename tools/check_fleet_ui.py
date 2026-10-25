#!/usr/bin/env python3
"""Check a reproduced native CustomUI identifier constraint before packaging."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
for p in (ROOT/'src/main/resources/Common/UI/Custom/Pages/OptimusLink').glob('*.ui'):
 text=re.sub(r'"(?:\\.|[^"\\])*"','""',p.read_text())
 bad=re.search(r'[#@]([A-Za-z][A-Za-z0-9]*_[A-Za-z0-9_]*)',text)
 if bad:raise SystemExit(f'{p.name}: native UI identifiers must be alphanumeric: {bad.group(1)}')
print('Fleet CustomUI identifier syntax: PASS')
