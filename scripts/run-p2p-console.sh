#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUTPUT="$(mktemp -d)"
trap 'rm -rf "$OUTPUT"' EXIT
javac -encoding UTF-8 -d "$OUTPUT" $(find src/main/java/seoultech/se/tetris/infra/network -name '*.java' | sort) \
  src/main/java/seoultech/se/tetris/infra/persistence/RecentIpRepository.java \
  scripts/p2p-smoke/P2PConsoleDemo.java
java -cp "$OUTPUT" P2PConsoleDemo "$@"
