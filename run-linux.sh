#!/bin/bash

# Configuration
PWD=$(pwd)

echo "Setting up X11 authority..."
XAUTH_FILE=$(xauth info 2>/dev/null | grep "Authority file" | awk '{ print $3 }')

mkdir -p .Xauthority
if [ -n "$XAUTH_FILE" ] && [ -f "$XAUTH_FILE" ]; then
    cat "$XAUTH_FILE" > .Xauthority/Xauthority
else
    # If no xauth file, just ensure the folder exists. X11 might still work via /tmp/.X11-unix
    touch .Xauthority/Xauthority
fi

echo "Starting Micro-Manager via Docker Compose..."
docker compose run --build --rm micro-manager
