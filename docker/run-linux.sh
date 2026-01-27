#!/bin/bash

# Ensure we are in the directory where the script is located
cd "$(dirname "$0")"

echo "Setting up X11 authority..."
XAUTH_FILE=$(xauth info 2>/dev/null | grep "Authority file" | awk '{ print $3 }')

# Create .Xauthority one level up (project root) to match compose mapping
mkdir -p ../.Xauthority
if [ -n "$XAUTH_FILE" ] && [ -f "$XAUTH_FILE" ]; then
    cat "$XAUTH_FILE" > ../.Xauthority/Xauthority
else
    touch ../.Xauthority/Xauthority
fi

echo "Starting Micro-Manager via Docker Compose..."
docker compose run --rm micro-manager
