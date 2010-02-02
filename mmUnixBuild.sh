#!/bin/bash
# enable error handling
set -e

# bootstrap autotools
aclocal -I m4
libtoolize --force
automake --foreign --add-missing
autoconf -I m4
cd DeviceAdapters
aclocal 
libtoolize --force
automake --foreign --add-missing
autoconf
cd ..
