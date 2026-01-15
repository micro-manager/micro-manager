#!/bin/bash
set -e

# Script to rebuild only C++ components (mmCoreAndDevices) without Java
# Usage: Run this inside the container after modifying mmCoreAndDevices

MM_SRC_DIR=/home/engineer/mm-src/micro-manager
IMAGEJ_DIR=/home/engineer/ImageJ

cd $MM_SRC_DIR

echo "Cleaning previous build artifacts..."
make clean || true

echo "Reconfiguring without Java..."
./configure --without-java --enable-imagej-plugin=$IMAGEJ_DIR

echo "Building C++ components only with ccache..."
make -j$(nproc)

echo "Installing to ImageJ..."
make install

echo "Done! C++ components rebuilt successfully."
