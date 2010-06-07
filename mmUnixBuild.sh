#!/bin/bash
# enable error handling
set -e


# bootstrap autotools
mkdir SecretDeviceAdapters || echo "SecretDeviceAdapters present"
aclocal -I m4
libtoolize --force
automake --foreign --add-missing
autoconf -I m4
cd DeviceAdapters
aclocal -I ../m4
libtoolize --force
automake --foreign --add-missing
autoconf -I ../m4
cd ..
cd Test_Programs
aclocal -I ../m4
libtoolize --force
automake --foreign --add-missing
autoconf -I ../m4
cd ..
if test -r "SecretDeviceAdapters/configure.in" ;  then
   cd SecretDeviceAdapters
   aclocal 
   libtoolize --force
   automake --foreign --add-missing
   autoconf
   cd ..
fi
