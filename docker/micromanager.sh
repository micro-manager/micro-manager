#!/bin/sh

# Micro-Manager launcher for running as ImageJ plugin.

IMAGEJ_DIR="/opt/ImageJ"

# Include proprietary drivers in library path if they exist
export LD_LIBRARY_PATH="$IMAGEJ_DIR/drivers:$LD_LIBRARY_PATH"

umask 0002 && java -Xmx1024M \
   -XX:MaxDirectMemorySize=1000G \
   -Dmmcorej.library.loading.stderr.log=yes \
   -Dorg.micromanager.corelog.dir=/tmp \
   -Dplugins.dir="$IMAGEJ_DIR" \
   --add-opens=java.desktop/java.awt=ALL-UNNAMED \
   --add-opens=java.desktop/java.awt.color=ALL-UNNAMED \
   --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
   -jar "$IMAGEJ_DIR/ij.jar" \
   -eval 'run("Micro-Manager Studio");' \
