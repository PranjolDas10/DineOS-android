#!/usr/bin/env sh
# Copies the two files this app is generated from out of the backend repo (expected next to
# this one, or pass its path). Run it after the API or the design tokens change, then build.
set -eu
backend="${1:-../DineOS}"
cd "$(dirname "$0")/.."
cp "$backend/web/openapi.json" contract/openapi.json
cp "$backend/design/tokens.json" design/tokens.json
node design/build-tokens.mjs
git status --short contract design app/src/main/java/dev/dineos/app/ui/theme/Tokens.kt
