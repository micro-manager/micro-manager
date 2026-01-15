#!/bin/bash
set -e

# Script to build Docker image and test C++ compilation
# This provides a deterministic way to verify mmCoreAndDevices changes compile

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="micro-manager-dev"

echo "==================================="
echo "Building Micro-Manager Docker Image"
echo "==================================="
echo ""

# Enable Docker BuildKit for cache mount support
export DOCKER_BUILDKIT=1

# Build the Docker image
docker build -t "$IMAGE_NAME" -f "$SCRIPT_DIR/docker/Dockerfile" "$SCRIPT_DIR"

echo ""
echo "==================================="
echo "Running C++ Rebuild in Container"
echo "==================================="
echo ""

# Run the container with mmCoreAndDevices mounted and execute rebuild-cpp.sh
docker run --rm \
  -v "$SCRIPT_DIR/mmCoreAndDevices:/home/engineer/mm-src/micro-manager/mmCoreAndDevices" \
  "$IMAGE_NAME" \
  rebuild-cpp

echo ""
echo "==================================="
echo "Build and test completed successfully!"
echo "==================================="
