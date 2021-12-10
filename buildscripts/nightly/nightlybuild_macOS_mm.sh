#!/bin/bash

set -e

usage() {
   echo "Usage: $0 [-cCIr] [-D PATH] [-R | -v VERSION]" 1>&2
   echo "   -r         -- skip autogen.sh" 1>&2
   echo "   -C         -- skip ./configure" 1>&2
   echo "   -I         -- do not create disk image" 1>&2
   echo "   -c         -- print the ./configure command line and exit" 1>&2
   echo "   -D PATH    -- use dependencies at prefix PATH" 1>&2
   echo "   -R         -- use release version string (no date)" 1>&2
   echo "   -v VERSION -- set version string" 1>&2
   echo "Environment:" 1>&2
   echo "   MAKEFLAGS  -- flags to pass to make(1) for building" 1>&2
   exit 1
}

skip_autogen=no
skip_config=no
make_disk_image=yes
print_config_only=no
use_release_version=no
while getopts ":rIcCD:Rv:" o; do
   case $o in
      r) skip_autogen=yes ;;
      C) skip_config=yes ;;
      I) make_disk_image=no ;;
      c) print_config_only=yes ;;
      D) MM_DEPS_PREFIX="$OPTARG" ;;
      R) use_release_version=yes ;;
      v) MM_VERSION="$OPTARG" ;;
      *) usage ;;
   esac
done


##
## Setup
##

source "`dirname $0`/nightlybuild_macOS_defs.sh"
pushd "`dirname $0`/../.." >/dev/null; MM_SRCDIR=`pwd`; popd >/dev/null

# GNU libtool (i.e. any libtoolized project) can mess around with the value of
# MACOSX_DEPLOYMENT_TARGET, so passing the correct compiler and linker flags
# (clang -mmacosx-version-min=10.9; ld -macosx_version_min 10.9) is not enough;
# we need to set this environment variable. It is also simpler than using
# command line flags.  Do the same for SDKROOT (instead of clang -isysroot; ld
# -syslibroot).
# Note that manually running e.g. `make' on directories configured with this
# script requires manually setting these environment variables. Failure to do
# so will result in broken binaries.
export MACOSX_DEPLOYMENT_TARGET=$MM_MACOSX_VERSION_MIN
export SDKROOT=$MM_MACOSX_SDKROOT


##
## Build
##

cd $MM_SRCDIR

if [ -z "$MM_VERSION" ]; then
   MM_VERSION="$(cat version.txt)"
   [ "$use_release_version" = yes ] || MM_VERSION="$MM_VERSION-$(date +%Y%m%d)"
fi
sed -e "s/@VERSION_STRING@/$MM_VERSION/" buildscripts/MMVersion.java.in > mmstudio/src/main/java/org/micromanager/internal/MMVersion.java || exit

if [ "$skip_autogen" != yes ]; then
   sh autogen.sh
fi

# Note on OpenCV library flags.
# Since OpenCV is a CMake project, it does not produce the convenient libtool
# .la files that specify the link dependencies for its static libraries. It
# also produces broken pkg-config metadata (on OS X, at least), so the LIBS
# need to be given manually. The list of dependencies can be obtained from
# either the pkg-config .pc file (after appropriate corrections) or from the
# opencv_*_LIB_DEPENS:STATIC entries in CMakeCache.txt. The built
# libmmgr_dal_OpenCVgrabber should be checked for undefined symbols in the flat
# namespace using nm -m ... | grep 'dynamically looked up'
# The C++ standard library (libstdc++ if deployment target <= 10.8) need not be
# specified as we are using the C++ linker driver.

# Note on libusb library flags.
# It looks like both libusb-1.0 and libusb-compat fail to include IOKit and
# CoreFoundation in their respective .la files.

if [ "$print_config_only" = yes ]; then
   EVAL="eval printf '\"%s\" '"
else
   EVAL=eval
fi

if [ "$skip_config" != yes ]; then

if [ "$print_config_only" = yes ]; then
   printf "MACOSX_DEPLOYMENT_TARGET=\"$MM_MACOSX_VERSION_MIN\" SDKROOT=\"$MM_MACOSX_SDKROOT\" "
fi
$EVAL ./configure \
   --prefix=$MM_BUILDDIR/it-is-a-bug-if-files-go-in-here \
   --disable-hardcoded-mmcorej-library-path \
   --with-boost=$MM_DEPS_PREFIX \
   --with-libdc1394 \
   --with-libusb-0-1 \
   --with-hidapi \
   --with-opencv \
   --with-gphoto2 \
   --with-freeimageplus \
   $MM_CONFIGUREFLAGS \
   "JAVA_HOME=\"$MM_JDK_HOME\"" \
   "JNI_CPPFLAGS=\"-I$MM_JDK_HOME/include -I$MM_JDK_HOME/include/darwin\"" \
   "JAVACFLAGS=\"-Xlint:all,-path,-serial -source 1.8 -target 1.8\"" \
   "OPENCV_LDFLAGS=\"-framework Cocoa -framework QTKit -framework QuartzCore -framework AppKit\"" \
   "OPENCV_LIBS=\"$MM_DEPS_PREFIX/lib/libopencv_highgui.a $MM_DEPS_PREFIX/lib/libopencv_imgproc.a $MM_DEPS_PREFIX/lib/libopencv_core.a -lz $MM_DEPS_PREFIX/lib/libdc1394.la\"" \
   PKG_CONFIG=$MM_DEPS_PREFIX/bin/pkg-config \
   "LIBUSB_0_1_LDFLAGS=\"-framework IOKit -framework CoreFoundation\"" \
   LIBUSB_0_1_LIBS=$MM_DEPS_PREFIX/lib/libusb.la \
   HIDAPI_LIBS=$MM_DEPS_PREFIX/lib/libhidapi.la
if [ "$print_config_only" = yes ]; then
   printf \\n
fi

fi # skip_config

if [ "$print_config_only" = yes ]; then
   exit 0
fi


make fetchdeps # Safe, since everything is checksummed

make $MAKEFLAGS

# Remove device adapters that build for x86_64 but depend on 32-bit-only
# frameworks. This is only for safety; these adapters should not build if their
# dependencies are not installed in /Library/Frameworks.
for file in mmCoreAndDevices/DeviceAdapters/PVCAM/.libs/libmmgr_dal_PVCAM \
            mmCoreAndDevices/DeviceAdapters/PrincetonInstruments/.libs/libmmgr_dal_PrincetonInstruments \
            mmCoreAndDevices/DeviceAdapters/QCam/.libs/libmmgr_dal_QCam \
            mmCoreAndDevices/DeviceAdapters/ScionCam/.libs/libmmgr_dal_ScionCam \
            mmCoreAndDevices/DeviceAdapters/Spot/.libs/libmmgr_dal_Spot \
            mmCoreAndDevices/SecretDeviceAdapters/HamamatsuMac/.libs/libmmgr_dal_Hamamatsu
do
   rm -f $file
done


##
## Stage application
##

MM_JARDIR=$MM_STAGEDIR/plugins/Micro-Manager
make install pkglibdir=$MM_STAGEDIR pkgdatadir=$MM_STAGEDIR jardir=$MM_JARDIR
rm -f $MM_STAGEDIR/*.la


# Stage other files
cp -R $MM_SRCDIR/bindist/any-platform/* $MM_STAGEDIR/
cp -R $MM_SRCDIR/bindist/MacOSX/* $MM_STAGEDIR/


# Stage the libgphoto2 dylibs.
mkdir -p $MM_STAGEDIR/libgphoto2/libgphoto2
mkdir -p $MM_STAGEDIR/libgphoto2/libgphoto2_port
cp $MM_DEPS_PREFIX/lib/libgphoto2/2.5.2/*.so $MM_STAGEDIR/libgphoto2/libgphoto2
cp $MM_DEPS_PREFIX/lib/libgphoto2_port/0.10.0/*.so $MM_STAGEDIR/libgphoto2/libgphoto2_port
echo 'Staging portable app with mkportableapp.py...'
buildscripts/nightly/mkportableapp_OSX/mkportableapp.py \
   --verbose \
   --srcdir $MM_DEPS_PREFIX/lib \
   --destdir $MM_STAGEDIR \
   --forbid-from $MM_BUILDDIR/share \
   --forbid-from $MM_DEPS_PREFIX/src \
   --forbid-from $MM_DEPS_PREFIX/share \
   --forbid-from /usr/local \
   --map-path 'libltdl*.dylib:libgphoto2' \
   --map-path 'libgphoto2*.dylib:libgphoto2'
echo 'Finished staging portable app'


# Stage third-party JARs.
for artifact_dir in compile optional runtime; do
   if ls $MM_SRCDIR/dependencies/artifacts/$artifact_dir/*.jar 1>/dev/null; then
      cp $MM_SRCDIR/dependencies/artifacts/$artifact_dir/*.jar $MM_JARDIR
   fi
done
# Include jogl/gluegen native libraries.  
mkdir -p $MM_STAGEDIR/natives/macosx-universal
cp ../3rdpartypublic/javalib3d/lib/natives/macosx-universal/* $MM_STAGEDIR/natives/macosx-universal/

# ij.jar goes into the ImageJ.app directory
# Note: this copying step messes up the code signing that previously happened
# So, for now, rely on the copy of ij.jar in the repository.  
# Revisit this once we are signing the app ourselves.
#mkdir -p $MM_STAGEDIR/ImageJ.app/Contents/Java
#cp $MM_SRCDIR/dependencies/artifacts/imagej/ij-*.jar $MM_STAGEDIR/ImageJ.app/Contents/Java/ij.jar

# Ensure no SVN data gets into the installer (e.g. when copying from bindist/)
find $MM_STAGEDIR -name .svn -prune -exec rm -rf {} +

if [ -n "$MM_PREPACKAGE_HOOK" ]; then
   pushd $MM_STAGEDIR
   $MM_PREPACKAGE_HOOK
   popd
fi

# Apply ad-hoc signature to the launchers, to prevent "damaged" messages on
# Mountain Lion and later.
# This now creates problems.  Try to verbatim copy the ImageJ.app
# Revisit once we have our own developer keys
# codesign -s - -f $MM_STAGEDIR/ImageJ.app

if [ "$make_disk_image" != yes ]; then
   exit 0
fi


##
## Create disk image
##

cd $MM_BUILDDIR
rm -f Micro-Manager.dmg Micro-Manager.sparseimage

hdiutil convert $MM_SRCDIR/buildscripts/MacInstaller/Micro-Manager.dmg -format UDSP -o Micro-Manager.sparseimage
mkdir -p mm-mnt
hdiutil attach Micro-Manager.sparseimage -mountpoint mm-mnt
cp -R $MM_STAGEDIR/* mm-mnt/Micro-Manager
mv mm-mnt/Micro-Manager mm-mnt/Micro-Manager-$MM_VERSION
hdiutil detach mm-mnt
rmdir mm-mnt
hdiutil convert Micro-Manager.sparseimage -format UDBZ -o Micro-Manager-$MM_VERSION.dmg
