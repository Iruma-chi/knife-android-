#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMPORTER="$ROOT/app/src/main/cpp/nximport"
TEST="$ROOT/app/src/test/cpp/nximport_test.cpp"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
"${CXX:-g++}" -std=c++20 -O2 -Wall -Wextra -Wpedantic \
  -I "$IMPORTER" "$TEST" "$IMPORTER/nximport.cpp" "$IMPORTER/nxcrypto.cpp" \
  -o "$TMP/nximport_test"
"$TMP/nximport_test"
