#!/usr/bin/env python3
"""Build the guide strictly with the same pinned epoch as the development archive."""
from pathlib import Path
import datetime
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
target = json.loads((ROOT / 'target.json').read_text())
archive_date = datetime.datetime.fromisoformat(target['archive_date'].replace('Z', '+00:00'))
env = dict(os.environ)
env['SOURCE_DATE_EPOCH'] = str(int(archive_date.timestamp()))
subprocess.run([sys.executable, '-m', 'mkdocs', 'build', '--strict'], cwd=ROOT, env=env, check=True)
