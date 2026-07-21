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
   case "$MM_UI_SCALE" in
      ''|*[!0-9.]*)
         echo "Invalid MM_UI_SCALE: '$MM_UI_SCALE' (expected a positive number, e.g. 2 or 1.5)" >&2
         exit 1
         ;;
   esac
   MM_UI_SCALE_OPT="-Dsun.java2d.uiScale=$MM_UI_SCALE"
fi

umask 0002 && java -Xmx1024M \
   -XX:MaxDirectMemorySize=1000G \
   -Dmmcorej.library.loading.stderr.log=yes \
   -Dorg.micromanager.corelog.dir=/tmp \
   -Dplugins.dir="$IMAGEJ_DIR" \
   $MM_UI_SCALE_OPT \
   -jar "$IMAGEJ_DIR/ij.jar" \
   -eval 'run("Micro-Manager Studio");' \
