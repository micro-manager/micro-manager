#!/bin/bash
set -e

# Flexible entrypoint that can run different commands
# Usage:
#   docker run <image>                    -> runs micromanager
#   docker run <image> rebuild-cpp        -> rebuilds C++ only
#   docker run <image> <any other command> -> runs that command

if [ $# -eq 0 ]; then
    # No arguments: run micromanager
    exec /home/engineer/ImageJ/micromanager.sh
elif [ "$1" = "rebuild-cpp" ]; then
    # Rebuild C++ components only
    exec /home/engineer/rebuild-cpp.sh
else
    # Run whatever command was passed
    exec "$@"
fi
