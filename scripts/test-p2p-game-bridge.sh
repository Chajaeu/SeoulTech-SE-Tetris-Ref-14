#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUTPUT="$(mktemp -d)"
trap 'rm -rf "$OUTPUT"' EXIT
# 기존 Game/Cell/Mino/Board 클래스와 Infra 네트워크만 컴파일, JavaFX는 불필요.
javac -encoding UTF-8 -d "$OUTPUT" \
  $(find src/main/java/seoultech/se/tetris/game -maxdepth 1 -name '*.java' | sort) \
  $(find src/main/java/seoultech/se/tetris/infra/network -maxdepth 1 -name '*.java' | sort) \
  src/main/java/seoultech/se/tetris/infra/integration/GameSnapshotAdapter.java \
  src/main/java/seoultech/se/tetris/infra/integration/P2PGameBridge.java \
  src/main/java/seoultech/se/tetris/infra/persistence/RecentIpRepository.java \
  scripts/p2p-smoke/P2PGameBridgeSmoke.java
java -cp "$OUTPUT" seoultech.se.tetris.infra.integration.P2PGameBridgeSmoke
