#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/infrai-media-archive-run"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java' -print)
exec java -cp "$BUILD_DIR" dev.infrai.mediaarchive.VideoSessionArchive
