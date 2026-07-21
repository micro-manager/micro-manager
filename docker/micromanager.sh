#!/bin/sh

# Micro-Manager launcher for running as ImageJ plugin.

IMAGEJ_DIR="/opt/ImageJ"

# Include proprietary drivers in library path if they exist
export LD_LIBRARY_PATH="$IMAGEJ_DIR/drivers:$LD_LIBRARY_PATH"

# --enable-native-access is only understood by JDK 17+; older but still
# supported JDKs (e.g. 11) fail to start entirely if passed an option they
# don't recognize, so only pass it when the selected JDK accepts it.
JAVA_VER=$(java -version 2>&1 | awk -F'"' '/version/ {print $2}')
JAVA_MAJOR=$(echo "$JAVA_VER" | awk -F. '{ if ($1 == 1) print $2; else print $1 }')
NATIVE_ACCESS_OPT=
if [ "$JAVA_MAJOR" -ge 17 ] 2>/dev/null; then
   NATIVE_ACCESS_OPT="--enable-native-access=ALL-UNNAMED"
fi

umask 0002 && java -Xmx1024M \
   -XX:MaxDirectMemorySize=1000G \
   -Dmmcorej.library.loading.stderr.log=yes \
   -Dorg.micromanager.corelog.dir=/tmp \
   -Dplugins.dir="$IMAGEJ_DIR" \
   --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
   $NATIVE_ACCESS_OPT \
   -jar "$IMAGEJ_DIR/ij.jar" \
   -eval 'run("Micro-Manager Studio");' \
