#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV="$ROOT/polish-learning-web/.tts-venv"

if [[ ! -x "$VENV/bin/python" ]]; then
  python3 -m venv "$VENV"
fi

"$VENV/bin/python" -m pip install -r "$ROOT/polish-learning-web/requirements-tts.txt"
exec "$VENV/bin/python" -m uvicorn server:app --app-dir "$ROOT/polish-learning-web" --host 127.0.0.1 --port 48733
