#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
test_out="$(mktemp -d)"
trap 'rm -rf "$test_out"' EXIT
javac -encoding UTF-8 -d "$test_out" "$project_root"/app/src/main/java/io/github/haroonjadoon/firmscope/core/*.java "$project_root/tests/FirmwareCoreTest.java"
java -cp "$test_out" FirmwareCoreTest "$project_root/tests/fixtures"
