#!/usr/bin/env bash
# Compiles the project with the JDK's javac (no build tool required).
set -euo pipefail

cd "$(dirname "$0")"

echo "Compiling main sources..."
mkdir -p out
find src/main -name "*.java" > sources.txt
javac -d out @sources.txt

echo "Compiling test sources..."
find src/main src/test -name "*.java" > sources.txt
javac -d out @sources.txt
rm -f sources.txt

echo "Build complete. Classes written to ./out"
