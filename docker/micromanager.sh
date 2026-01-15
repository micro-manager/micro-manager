#!/bin/sh

# Micro-Manager launcher for running as ImageJ plugin.

cd /home/engineer/ImageJ

umask 0002 && java -Xmx1024M \
   -XX:MaxDirectMemorySize=1000G \
   -Dmmcorej.library.loading.stderr.log=yes \
   -Dorg.micromanager.corelog.dir=/tmp \
   --add-opens=java.desktop/java.awt=ALL-UNNAMED \
   --add-opens=java.desktop/java.awt.color=ALL-UNNAMED \
   --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
   -jar "ij.jar" \
   -eval 'run("Micro-Manager Studio");' \
