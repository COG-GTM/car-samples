#!/usr/bin/env bash
# Renders the Graphviz sources in this directory to PNG and SVG.
# Needs Graphviz: sudo apt-get install -y graphviz fonts-dejavu
set -euo pipefail
cd "$(dirname "$0")"
for src in *.dot; do
  name="${src%.dot}"
  dot -Tpng -Gdpi=110 "$src" -o "$name.png"
  dot -Tsvg "$src" -o "$name.svg"
done
