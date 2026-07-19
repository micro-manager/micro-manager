#!/bin/sh

# Micro-Manager launcher for running as ImageJ plugin.

IMAGEJ_DIR="/opt/ImageJ"

# Include proprietary drivers in library path if they exist
export LD_LIBRARY_PATH="$IMAGEJ_DIR/drivers:$LD_LIBRARY_PATH"

# Java's HiDPI auto-detection is unreliable on Linux/X11. Set MM_UI_SCALE
# (e.g. MM_UI_SCALE=2) to force a scale factor if the UI appears too small
# when X11-forwarding to a HiDPI host display.
MM_UI_SCALE_OPT=
if [ -n "$MM_UI_SCALE" ]; then
   MM_UI_SCALE_OPT="-Dsun.java2d.uiScale=$MM_UI_SCALE"
fi

umask 0002 && java -Xmx1024M \
   -XX:MaxDirectMemorySize=1000G \
   -Dmmcorej.library.loading.stderr.log=yes \
   -Dorg.micromanager.corelog.dir=/tmp \
   -Dplugins.dir="$IMAGEJ_DIR" \
   --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
   --enable-native-access=ALL-UNNAMED \
   $MM_UI_SCALE_OPT \
   -jar "$IMAGEJ_DIR/ij.jar" \
   -eval 'run("Micro-Manager Studio");' \
