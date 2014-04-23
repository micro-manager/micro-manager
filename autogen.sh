#!/bin/sh


echo "Bootstrapping autoconf/automake build system for Micro-Manager..." 1>&2

# Subdirectory must be present, even if empty, to prevent automake errors.
mkdir -p SecretDeviceAdapters

autoreconf --force --install --verbose

echo "Bootstrapping complete; now you can run ./configure" 1>&2
echo "If you would like to install Micro-Manager as an ImageJ plugin, run" 1>&2
echo "  ./configure --enable-imagej-plugin=/path/to/ImageJ" 1>&2
