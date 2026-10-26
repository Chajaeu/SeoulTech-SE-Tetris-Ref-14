#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

APP_NAME="Team14Tetris"
LOGO="src/main/resources/seoultech/se/tetris/ui/img/Main_Logo.png"

if [ ! -f "$LOGO" ]; then
    echo "ERROR: TETRIS 로고 파일이 없습니다."
    exit 1
fi

ICON_TMP=$(mktemp -d /private/tmp/team14-icon.XXXXXX)
PKG_TMP=$(mktemp -d /private/tmp/team14-package.XXXXXX)
trap 'rm -rf "$ICON_TMP" "$PKG_TMP"' EXIT

mkdir -p packaging build/dist
mkdir -p "$ICON_TMP/Team14Tetris.iconset"
mkdir -p "$PKG_TMP/output" "$PKG_TMP/temp"

echo "[1/5] 원본 TETRIS 로고로 아이콘 생성"

sips -Z 1000 "$LOGO" \
  --out "$ICON_TMP/resized.png" >/dev/null

sips --padToHeightWidth 1024 1024 \
  --padColor 000000 \
  "$ICON_TMP/resized.png" \
  --out packaging/Team14TetrisIcon.png >/dev/null

for spec in \
  "16:icon_16x16.png" \
  "32:icon_16x16@2x.png" \
  "32:icon_32x32.png" \
  "64:icon_32x32@2x.png" \
  "128:icon_128x128.png" \
  "256:icon_128x128@2x.png" \
  "256:icon_256x256.png" \
  "512:icon_256x256@2x.png" \
  "512:icon_512x512.png" \
  "1024:icon_512x512@2x.png"
do
    size="${spec%%:*}"
    name="${spec#*:}"
    sips -z "$size" "$size" \
      packaging/Team14TetrisIcon.png \
      --out "$ICON_TMP/Team14Tetris.iconset/$name" >/dev/null
done

iconutil -c icns \
  "$ICON_TMP/Team14Tetris.iconset" \
  -o packaging/Team14Tetris.icns

echo "[2/5] Gradle 배포 빌드"
./gradlew clean installDist

echo "[3/5] macOS 앱 생성"
jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --dest "$PKG_TMP/output" \
  --temp "$PKG_TMP/temp" \
  --input build/install/seoultech-se-tetris-team14/lib \
  --main-jar seoultech-se-tetris-team14.jar \
  --main-class seoultech.se.tetris.ui.MainMenuPreview \
  --app-version 1.0 \
  --icon packaging/Team14Tetris.icns

echo "[4/5] 임시 경로에서 코드 서명 검증"
codesign --verify --deep --strict --verbose=2 \
  "$PKG_TMP/output/$APP_NAME.app"

echo "[5/5] 검증된 앱을 ZIP으로 패키징"
ZIP_NAME="${APP_NAME}-macOS-$(uname -m).zip"

ditto -c -k --keepParent \
  "$PKG_TMP/output/$APP_NAME.app" \
  "$PKG_TMP/output/$ZIP_NAME"

mkdir -p build/dist
cp "$PKG_TMP/output/$ZIP_NAME" "build/dist/$ZIP_NAME"

echo ""
echo "SUCCESS: macOS 배포 패키지 생성 완료"
echo "ZIP: build/dist/$ZIP_NAME"
echo "아이콘: packaging/Team14Tetris.icns"
