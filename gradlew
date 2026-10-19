#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

case "${1:-build}" in
    build|assemble|jar)
        exec "$SCRIPT_DIR/build.sh"
        ;;
    clean)
        rm -rf "$SCRIPT_DIR/bin" "$SCRIPT_DIR/build" /tmp/deathdrop-classes
        ;;
    --version|-v)
        printf '%s\n' 'DeathDrop build wrapper (local javac build)'
        ;;
    *)
        printf 'Usage: %s [build|assemble|jar|clean|--version]\n' "$(basename "$0")" >&2
        exit 1
        ;;
esac