#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 BryceWG
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Downloads the SenseVoice model used by the in-process speech recognizer and places it
# under app/src/main/assets/sensevoice/ so it is bundled into the APK.
#
# The files are large (~228 MB) and intentionally not committed to git.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$REPO_ROOT/app/src/main/assets/sensevoice"
MODEL_FILE="$TARGET_DIR/model.int8.onnx"
TOKENS_FILE="$TARGET_DIR/tokens.txt"

# sherpa-onnx SenseVoice int8 model (zh/en/ja/ko/yue)
BASE_URL="https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main"
BASE_URL_FALLBACK="https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main"

download() {
    local url="$1"
    local out="$2"
    echo "Downloading $out from $url"
    curl -L --fail --retry 5 --retry-all-errors --retry-delay 3 \
        --connect-timeout 30 --progress-bar -o "$out.part" "$url"
    mv "$out.part" "$out"
}

if [[ -s "$MODEL_FILE" && -s "$TOKENS_FILE" ]]; then
    echo "SenseVoice model already present, skipping download."
    ls -lh "$MODEL_FILE" "$TOKENS_FILE"
    exit 0
fi

mkdir -p "$TARGET_DIR"

if [[ ! -s "$TOKENS_FILE" ]]; then
    download "$BASE_URL/tokens.txt" "$TOKENS_FILE" || \
        download "$BASE_URL_FALLBACK/tokens.txt" "$TOKENS_FILE"
fi

if [[ ! -s "$MODEL_FILE" ]]; then
    download "$BASE_URL/model.int8.onnx" "$MODEL_FILE" || \
        download "$BASE_URL_FALLBACK/model.int8.onnx" "$MODEL_FILE"
fi

if [[ ! -s "$MODEL_FILE" || ! -s "$TOKENS_FILE" ]]; then
    echo "ERROR: SenseVoice model download incomplete" >&2
    exit 1
fi

echo "SenseVoice model ready:"
ls -lh "$MODEL_FILE" "$TOKENS_FILE"
