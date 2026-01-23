#!/bin/bash
set -e

# This script is called by the Dockerfile during the builder stage.
# It optionally installs the PVCAM SDK if the files are present.

ZIP_FILE="./docker/drivers/PVCAM-Linux-3-10-0-3.zip"

if [ -f "$ZIP_FILE" ]; then
    echo "--- Unpacking PVCAM Driver Zip ---"
    # Extract zip file which contains pvcam and pvcam-sdk folders
    unzip -o "$ZIP_FILE"
else
    echo "PVCAM zip file not found at $ZIP_FILE, skipping unpack."
fi

# Find the newest .run installer in the pvcam-sdk directory
INSTALLER=$(find ./pvcam-sdk -name "pvcam-sdk_*.run" | sort -V | tail -n1 2>/dev/null)

if [ -n "$INSTALLER" ]; then
    echo "--- Installing PVCAM SDK Dependencies ---"
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libtiff5-dev \
        libtiff5 \
        libwxgtk3.0-gtk3-dev || echo "Warning: Some dependencies could not be found, attempting to continue..."

    echo "--- Running PVCAM SDK Installer ---"
    # Run installer in quiet mode
    # We pipe 'yes' to handle license agreement prompts
    chmod +x "$INSTALLER"
    yes | bash "$INSTALLER" -q -- -q

    echo "--- Post-installation Setup ---"
    # PVCAM typically installs to /opt/pvcam or /usr/local
    # Ensure headers and libs are in standard search paths for the Micro-Manager build
    if [ -d "/opt/pvcam" ]; then
        echo "Linking PVCAM files from /opt/pvcam to /usr/local..."
        [ -d "/opt/pvcam/include" ] && ln -sf -- /opt/pvcam/include/* /usr/local/include/
        [ -d "/opt/pvcam/lib" ] && ln -sf -- /opt/pvcam/lib/* /usr/local/lib/
        ldconfig
    fi

    # Preparation for the Final Image Stage:
    echo "Staging libraries for final image..."
    mkdir -p /root/ImageJ/drivers
    if [ -d "/opt/pvcam/lib" ]; then
        cp -P /opt/pvcam/lib/*.so* /root/ImageJ/drivers/
    fi
    echo "PVCAM SDK setup complete."
else
    echo "PVCAM SDK installer not found in ./pvcam-sdk, skipping PVCAM installation."
fi
