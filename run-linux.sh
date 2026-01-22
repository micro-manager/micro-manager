#!/bin/bash

# Configuration
IMAGE_NAME="micro-manager-dev"
CONTAINER_NAME="micromanager-container"
PWD=$(pwd)

echo "Setting up X11 authority..."
XAUTH_FILE=$(xauth info | grep "Authority file" | awk '{ print $3 }')
mkdir -p .Xauthority
if [ -f "$XAUTH_FILE" ]; then
    cat "$XAUTH_FILE" > .Xauthority/Xauthority
else
    # Fallback to creating a dummy if not found, though this rarely works for X11
    touch .Xauthority/Xauthority
fi

echo "Starting Micro-Manager via Docker Compose..."
docker compose run --rm micro-manager
