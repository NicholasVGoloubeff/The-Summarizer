#!/usr/bin/env bash
set -e

# Simple dev runner for LectureNav backend.
# 1) Loads environment variables from .env
# 2) Runs Maven clean + spring-boot:run

# Load .env if present
if [ -f ".env" ]; then
  # shellcheck source=/dev/null
  source ".env"
fi

# Safety check: require CEREBRAS_API_KEY
if [ -z "$CEREBRAS_API_KEY" ]; then
  echo "❌ CEREBRAS_API_KEY is not set."
  echo "   Set it in .env (CEREBRAS_API_KEY=...) or export it before running."
  exit 1
fi

echo "✅ Using CEREBRAS_API_KEY (hidden) and starting backend..."
echo

# One command that cleans, compiles, and runs the app
mvn clean spring-boot:run
