#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
umask 077
export HOST="${HOST:-127.0.0.1}"
export PORT="${PORT:-8080}"
exec node src/server.mjs
