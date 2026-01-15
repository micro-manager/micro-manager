#!/bin/bash

# Configuration
IMAGE_NAME="micro-manager-dev"
CONTAINER_NAME="micromanager-container"
PWD=$(pwd)

echo "Setting up X11 authority..."
# Create local Xauthority directory to match Makefile logic
mkdir -p "$PWD"/.Xauthority 2> /dev/null
# Copy host Xauthority to local directory
XAUTH_FILE=$(xauth info | grep "Authority file" | awk '{ print $3 }')
if [ -f "$XAUTH_FILE" ]; then
    cat "$XAUTH_FILE" > "$PWD"/.Xauthority/Xauthority
else
    echo "Warning: No Xauthority file found at $XAUTH_FILE"
fi

echo "Starting Micro-Manager container ($IMAGE_NAME)..."

# Run the container with GUI support, device access, and volume mounts
docker run -it --rm \
    --name "$CONTAINER_NAME" \
    --network host \
    --privileged \
    -e DISPLAY="${DISPLAY}" \
    -e XAUTHORITY=/home/engineer/.Xauthority/Xauthority \
    -v /tmp/.X11-unix:/tmp/.X11-unix \
    -v "$PWD"/.Xauthority:/home/engineer/.Xauthority \
    -v /dev:/dev \
    -v "$PWD":/workdir \
    "$IMAGE_NAME"
