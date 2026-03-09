#!/bin/bash

# Project bootstrap script for ParkBuddy.
# Sets up local development environment.

set -e

echo "--- ParkBuddy Bootstrap ---"

# Setup Git Hooks - point to project hooks directory
git config core.hooksPath .githooks
echo "Success: Configured git hooks path."

echo "--- Bootstrap Complete ---"
echo "You can now commit changes, and they will be automatically formatted."
