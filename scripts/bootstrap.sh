#!/bin/bash

# Project bootstrap script for ParkBuddy.
# Sets up local development environment.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
KTFMT_VERSION="0.61"
KTFMT_JAR="$PROJECT_DIR/config/bin/ktfmt-$KTFMT_VERSION-with-dependencies.jar"

echo "--- ParkBuddy Bootstrap ---"

# Setup Git Hooks - point to project hooks directory
git config core.hooksPath .githooks
echo "Success: Configured git hooks path."

# Download ktfmt for formatting
if [ ! -f "$KTFMT_JAR" ]; then
  echo "Downloading ktfmt $KTFMT_VERSION..."
  mkdir -p "$(dirname "$KTFMT_JAR")"
  curl -sL "https://repo1.maven.org/maven2/com/facebook/ktfmt/$KTFMT_VERSION/ktfmt-$KTFMT_VERSION-with-dependencies.jar" \
    -o "$KTFMT_JAR"
  echo "Success: Downloaded ktfmt."
else
  echo "Success: ktfmt already present."
fi

echo "--- Bootstrap Complete ---"
echo "You can now commit changes, and they will be automatically formatted."
