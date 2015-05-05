#!/bin/bash

# Build external open-source library dependencies for Micro-Manager OS X build.
#
# All libraries are currently built as static-only libraries, except for
# libgphoto2 and its dependency libltdl, which need to be shared to work
# (because libgphoto2 uses libltdl for dynamic loading, and building a static
# version would not allow that due to libtool and libltdl's design).
#
# However, using dynamic libraries for dependencies could potentially simplify
# the build by eliminating the need to explicitly specify transitive
# dependencies in many cases. This would require that we write an install
# script that rewrites the library paths using @rpath or (better yet)
# @loader_path; see dyld(1).
#
# Third-party packages not represented here:
# - Build tools: should be installed using Homebrew (swig, cmake, autoconf,
#   automake, libtool, pkg-config, python3)
# - JDK: Apple JDK 6 from http://support.apple.com/kb/DL1572
#        Apple Java for OS X 2013-005 Developer Package (download requires
#        Apple Developer account)
# - Device-specific proprietary frameworks (currently in /Library/Frameworks)

# This script places and builds everything under $MM_DEPS_PREFIX.
# $MM_DEPS_PREFIX/downloads - tarballs
# $MM_DEPS_PREFIX/src - source and build (VPATH is not used)
# $MM_DEPS_PREFIX/include, $MM_DEPS_PREFIX/lib, etc. - staged libraries

set -e

usage() { echo "Usage: $0 [-d]" 1>&2; exit 1; }

do_download=no
while getopts ":d" o; do
   case "$o" in
      d) do_download=yes ;;
      *) usage ;;
   esac
done


##
## Setup
##

source "`dirname $0`/nightlybuild_OSX_defs.sh"

# GNU libtool (i.e. any libtoolized project) can mess around with the value of
# MACOSX_DEPLOYMENT_TARGET, so passing the correct compiler and linker flags
# (clang -mmacosx-version-min=10.5; ld -macosx_version_min 10.5) is not enough;
# we need to set this environment variable. It is also simpler than using
# command line flags.  Do the same for SDKROOT (instead of clang -isysroot; ld
# -syslibroot).
# Note that manually running e.g. `make' on directories configured with this
# script requires manually setting these environment variables. Failure to do
# so will result in broken binaries.
export MACOSX_DEPLOYMENT_TARGET=$MM_MACOSX_VERSION_MIN
export SDKROOT=$MM_MACOSX_SDKROOT


##
## Download
##

mkdir -p "$MM_DEPS_PREFIX"/downloads
cd "$MM_DEPS_PREFIX"/downloads
if [ "$do_download" = yes ]; then
   [ -f boost_1_55_0.tar.bz2 ] || curl -L -o boost_1_55_0.tar.bz2 http://sourceforge.net/projects/boost/files/boost/1.55.0/boost_1_55_0.tar.bz2/download
   [ -f libusb-1.0.18.tar.bz2 ] || curl -LO http://sourceforge.net/projects/libusb/files/libusb-1.0/libusb-1.0.18/libusb-1.0.18.tar.bz2
   [ -f libusb-compat-0.1.5.tar.bz2 ] || curl -LO http://sourceforge.net/projects/libusb/files/libusb-compat-0.1/libusb-compat-0.1.5/libusb-compat-0.1.5.tar.bz2
   [ -f hidapi-0.8.0-rc1.tar.gz ] || curl -LO https://github.com/signal11/hidapi/archive/hidapi-0.8.0-rc1.tar.gz
   [ -f libexif-0.6.21.tar.bz2 ] || curl -L -o libexif-0.6.21.tar.bz2 http://sourceforge.net/projects/libexif/files/libexif/0.6.21/libexif-0.6.21.tar.bz2/download
   [ -f libtool-2.4.2.tar.gz ] || curl -LO http://gnu.mirrorcatalogs.com/libtool/libtool-2.4.2.tar.gz
   [ -f libgphoto2-2.5.2.tar.bz2 ] || curl -L -o libgphoto2-2.5.2.tar.bz2 http://sourceforge.net/projects/gphoto/files/libgphoto/2.5.2/libgphoto2-2.5.2.tar.bz2/download
   [ -f FreeImage3154.zip ] || curl -LO http://downloads.sourceforge.net/freeimage/FreeImage3154.zip
   [ -f libdc1394-2.2.1.tar.gz ] || curl -L -o libdc1394-2.2.1.tar.gz http://sourceforge.net/projects/libdc1394/files/libdc1394-2/2.2.1/libdc1394-2.2.1.tar.gz/download
   [ -f opencv-2.4.9.zip ] || curl -L -o opencv-2.4.9.zip http://sourceforge.net/projects/opencvlibrary/files/opencv-unix/2.4.9/opencv-2.4.9.zip/download
fi

cat >sha1sums <<EOF
cef9a0cc7084b1d639e06cd3bc34e4251524c840  boost_1_55_0.tar.bz2
5f7bbf42a4d6e6b88d5e7666958c80f8455ee915  libusb-1.0.18.tar.bz2
062319276d913c753a4b1341036e6a2e42abccc9  libusb-compat-0.1.5.tar.bz2
5e72a4c7add8b85c8abcdd360ab8b1e1421da468  hidapi-0.8.0-rc1.tar.gz
a52219b12dbc8d33fc096468591170fda71316c0  libexif-0.6.21.tar.bz2
22b71a8b5ce3ad86e1094e7285981cae10e6ff88  libtool-2.4.2.tar.gz
6b70ff6feec62a955bef1fc9a2b16dd07f0e277a  libgphoto2-2.5.2.tar.bz2
1d30057a127b2016cf9b4f0f8f2ba92547670f96  FreeImage3154.zip
b92c9670b68c4e5011148f16c87532bef2e5b808  libdc1394-2.2.1.tar.gz
4f5166e2bd22bd6167cb56dd04f2c6ed68148b2c  opencv-2.4.9.zip
EOF
shasum -c sha1sums || { echo "SHA1 checksum mismatch or missing file; remove file and rerun with -d flag"; exit 1; }


##
## Build
##

mkdir -p "$MM_DEPS_PREFIX"/src
cd "$MM_DEPS_PREFIX"/src


#
# Boost
#

tar xjf ../downloads/boost_1_55_0.tar.bz2
pushd boost_1_55_0

# Boost 1.55.0 + Xcode 5.1 (Clang 3.4) requires patches to Boost.Atomic (1.56
# is expected to be fixed).

# The following two patches for Boost 1.55.0 are from GitHub, but we avoid
# downloding from this script because their checksums keep changing (due to
# subtle changes in the diff output, presumably corresponding to Git version
# differences).

# https://github.com/boostorg/atomic/commit/6bb71fdd.patch
patch -p2 <<'END_OF_PATCH'
From 6bb71fdd8f7cc346d90fb14beb38b7297fc1ffd9 Mon Sep 17 00:00:00 2001
From: Andrey Semashev <andrey.semashev@gmail.com>
Date: Sun, 26 Jan 2014 13:58:48 +0400
Subject: [PATCH] Fixed incorrect initialization of 128-bit values, when no
 native support for 128-bit integers is available.

---
 include/boost/atomic/detail/cas128strong.hpp | 10 +++++++---
 1 file changed, 7 insertions(+), 3 deletions(-)

diff --git a/include/boost/atomic/detail/cas128strong.hpp b/include/boost/atomic/detail/cas128strong.hpp
index 906c13e..dcb4d7d 100644
--- a/include/boost/atomic/detail/cas128strong.hpp
+++ b/include/boost/atomic/detail/cas128strong.hpp
@@ -196,15 +196,17 @@ class base_atomic<T, void, 16, Sign>
 
 public:
     BOOST_DEFAULTED_FUNCTION(base_atomic(void), {})
-    explicit base_atomic(value_type const& v) BOOST_NOEXCEPT : v_(0)
+    explicit base_atomic(value_type const& v) BOOST_NOEXCEPT
     {
+        memset(&v_, 0, sizeof(v_));
         memcpy(&v_, &v, sizeof(value_type));
     }
 
     void
     store(value_type const& value, memory_order order = memory_order_seq_cst) volatile BOOST_NOEXCEPT
     {
-        storage_type value_s = 0;
+        storage_type value_s;
+        memset(&value_s, 0, sizeof(value_s));
         memcpy(&value_s, &value, sizeof(value_type));
         platform_fence_before_store(order);
         platform_store128(value_s, &v_);
@@ -247,7 +249,9 @@ class base_atomic<T, void, 16, Sign>
         memory_order success_order,
         memory_order failure_order) volatile BOOST_NOEXCEPT
     {
-        storage_type expected_s = 0, desired_s = 0;
+        storage_type expected_s, desired_s;
+        memset(&expected_s, 0, sizeof(expected_s));
+        memset(&desired_s, 0, sizeof(desired_s));
         memcpy(&expected_s, &expected, sizeof(value_type));
         memcpy(&desired_s, &desired, sizeof(value_type));
 
END_OF_PATCH

# https://github.com/boostorg/atomic/commit/e4bde20f.patch
patch -p2 <<'END_OF_PATCH'
From e4bde20f2eec0a51be14533871d2123bd2ab9cf3 Mon Sep 17 00:00:00 2001
From: Andrey Semashev <andrey.semashev@gmail.com>
Date: Fri, 28 Feb 2014 12:43:11 +0400
Subject: [PATCH] More compilation fixes for the case when 128-bit integers are
 not supported.

---
 include/boost/atomic/detail/gcc-atomic.hpp | 17 ++++++++++++-----
 1 file changed, 12 insertions(+), 5 deletions(-)

diff --git a/include/boost/atomic/detail/gcc-atomic.hpp b/include/boost/atomic/detail/gcc-atomic.hpp
index a130590..4af99a1 100644
--- a/include/boost/atomic/detail/gcc-atomic.hpp
+++ b/include/boost/atomic/detail/gcc-atomic.hpp
@@ -958,14 +958,16 @@ class base_atomic<T, void, 16, Sign>
 
 public:
     BOOST_DEFAULTED_FUNCTION(base_atomic(void), {})
-    explicit base_atomic(value_type const& v) BOOST_NOEXCEPT : v_(0)
+    explicit base_atomic(value_type const& v) BOOST_NOEXCEPT
     {
+        memset(&v_, 0, sizeof(v_));
         memcpy(&v_, &v, sizeof(value_type));
     }
 
     void store(value_type const& v, memory_order order = memory_order_seq_cst) volatile BOOST_NOEXCEPT
     {
-        storage_type tmp = 0;
+        storage_type tmp;
+        memset(&tmp, 0, sizeof(tmp));
         memcpy(&tmp, &v, sizeof(value_type));
         __atomic_store_n(&v_, tmp, atomics::detail::convert_memory_order_to_gcc(order));
     }
@@ -980,7 +982,8 @@ class base_atomic<T, void, 16, Sign>
 
     value_type exchange(value_type const& v, memory_order order = memory_order_seq_cst) volatile BOOST_NOEXCEPT
     {
-        storage_type tmp = 0;
+        storage_type tmp;
+        memset(&tmp, 0, sizeof(tmp));
         memcpy(&tmp, &v, sizeof(value_type));
         tmp = __atomic_exchange_n(&v_, tmp, atomics::detail::convert_memory_order_to_gcc(order));
         value_type res;
@@ -994,7 +997,9 @@ class base_atomic<T, void, 16, Sign>
         memory_order success_order,
         memory_order failure_order) volatile BOOST_NOEXCEPT
     {
-        storage_type expected_s = 0, desired_s = 0;
+        storage_type expected_s, desired_s;
+        memset(&expected_s, 0, sizeof(expected_s));
+        memset(&desired_s, 0, sizeof(desired_s));
         memcpy(&expected_s, &expected, sizeof(value_type));
         memcpy(&desired_s, &desired, sizeof(value_type));
         const bool success = __atomic_compare_exchange_n(&v_, &expected_s, desired_s, false,
@@ -1010,7 +1015,9 @@ class base_atomic<T, void, 16, Sign>
         memory_order success_order,
         memory_order failure_order) volatile BOOST_NOEXCEPT
     {
-        storage_type expected_s = 0, desired_s = 0;
+        storage_type expected_s, desired_s;
+        memset(&expected_s, 0, sizeof(expected_s));
+        memset(&desired_s, 0, sizeof(desired_s));
         memcpy(&expected_s, &expected, sizeof(value_type));
         memcpy(&desired_s, &desired, sizeof(value_type));
         const bool success = __atomic_compare_exchange_n(&v_, &expected_s, desired_s, true,
END_OF_PATCH

# Now build the patched source.

./bootstrap.sh
./b2 --prefix=${MM_DEPS_PREFIX} link=static threading=multi architecture=x86 address-model=32_64 \
  cflags="${MM_CFLAGS}" cxxflags="${MM_CXXFLAGS}" \
  --with-atomic --with-chrono --with-date_time --with-filesystem --with-log --with-system --with-thread --with-timer \
  $MM_PARALLELMAKEFLAG install
popd


#
# libusb-1.0
#

tar xjf ../downloads/libusb-1.0.18.tar.bz2
pushd libusb-1.0.18
eval ./configure $MM_DEPS_CONFIGUREFLAGS --enable-static --disable-shared --with-pic
make $MM_PARALLELMAKEFLAG
make install
popd


#
# libusb-compat
# (depends on libusb-1.0)
#

tar xjf ../downloads/libusb-compat-0.1.5.tar.bz2
pushd libusb-compat-0.1.5
eval ./configure $MM_DEPS_CONFIGUREFLAGS --enable-static --disable-shared --with-pic PKG_CONFIG_PATH=$MM_DEPS_PREFIX/lib/pkgconfig
make $MM_PARALLELMAKEFLAG
make install
popd
 

#
# HIDAPI
#

tar xzf ../downloads/hidapi-0.8.0-rc1.tar.gz
pushd hidapi-hidapi-0.8.0-rc1
./bootstrap
eval ./configure $MM_DEPS_CONFIGUREFLAGS --enable-static --disable-shared --with-pic
make
make install
popd


#
# libexif
# (dependency of libgphoto2)
#

tar xjf ../downloads/libexif-0.6.21.tar.bz2
pushd libexif-0.6.21
eval ./configure $MM_DEPS_CONFIGUREFLAGS --enable-static --disable-shared --with-pic PKG_CONFIG_PATH=$MM_DEPS_PREFIX/lib/pkgconfig
make $MM_PARALLELMAKEFLAG
make install
popd


#
# libtool
#

tar xzf ../downloads/libtool-2.4.2.tar.gz
pushd libtool-2.4.2
eval ./configure $MM_DEPS_CONFIGUREFLAGS --enable-shared --disable-static --enable-ltdl-install
make $MM_PARALLELMAKEFLAG
make install
popd


#
# libgphoto2
# Currently, we use a shared library and rewrite the paths in the Makefile.am
# install target for libmmgr_dal_GPhoto.
#

tar xjf ../downloads/libgphoto2-2.5.2.tar.bz2
pushd libgphoto2-2.5.2

# The configure script requires explicit LTDLINCL, LIBLTDL, and LDFLAGS to find
# libltdl (which must of course be dual-arch).

# libxml2 from the Mac OS X SDK (i.e. the dylib bundled with Mac OS X) is used.

# _DARWIN_C_SOURCE needs to be defined for flock() calls to compile (see
# sys/fcntl.h).

# --without-libusb is key to prevent errors when both libusb-1.0 and libusb (0.1 or compat) are installed.


eval ./configure $MM_DEPS_CONFIGUREFLAGS_NOCPPLD "CPPFLAGS=\"\$MM_CPPFLAGS -isystem \$SDKROOT/usr/include/libxml2 -D_DARWIN_C_SOURCE\" LDFLAGS=\"\$MM_LDFLAGS\"" LTDLINCL=-I$MM_DEPS_PREFIX LIBLTDL=-lltdl PKG_CONFIG_PATH=$MM_DEPS_PREFIX/lib/pkgconfig --without-libusb "LIBUSB1_LIBS=\"-lusb-1.0 -framework IOKit -framework CoreFoundation\" LIBUSB1_CFLAGS=\"-I\$MM_DEPS_PREFIX/include/libusb-1.0\"" --enable-shared --disable-static
make $MM_PARALLELMAKEFLAG
make install
popd


#
# FreeImage
#

unzip -oq ../downloads/FreeImage3154.zip
pushd FreeImage

# FreeImage 3.15.4 comes with a Makefile.osx, but it is hardcoded to use
# outdated tools and is therefore useless. Replace the makefile with a minimal
# version for building a fat static library.
cat > Makefile.clang <<'END_OF_MAKEFILE'
include Makefile.srcs

CC = cc-from-cmdline
CXX = cxx-from-cmdline
CFLAGS = -Os -fexceptions -fvisibility=hidden -DPIC -fno-common
CXXFLAGS = $(CFLAGS) -Wno-ctor-dtor-privacy
CPPFLAGS = -DNO_LCMS $(MM_CPPFLAGS) $(INCLUDE)
LIBTOOL = /usr/bin/libtool

TARGET = freeimage
STATICLIB = lib$(TARGET).a
HEADER = Source/FreeImage.h

MODULES = $(SRCS:.c=.o)
MODULES := $(MODULES:.cpp=.o)

all: dist

dist: FreeImage
	cp $(STATICLIB) Dist
	cp $(HEADER) Dist

FreeImage: $(STATICLIB)

$(STATICLIB): $(MODULES)
	$(LIBTOOL) -static -o $@ $(MODULES)

clean:
	rm -f Dist/$(STATICLIB) Dist/$(HEADER) $(MODULES) $(STATICLIB)
END_OF_MAKEFILE

# Patch to add a missing #include
patch -p1 <<'END_OF_PATCH'
--- FreeImage3154/Source/OpenEXR/IlmImf/ImfAutoArray.h  2014-01-16 12:44:00.000000000 -0800
+++ FreeImage-patched/Source/OpenEXR/IlmImf/ImfAutoArray.h      2014-01-16 13:29:32.000000000 -0800
@@ -37,6 +37,8 @@
 #ifndef INCLUDED_IMF_AUTO_ARRAY_H
 #define INCLUDED_IMF_AUTO_ARRAY_H

+#include <string.h>
+
 //-----------------------------------------------------------------------------
 //
 //     class AutoArray -- a workaround for systems with
END_OF_PATCH

make -f Makefile.clang $MM_PARALLELMAKEFLAG CC="$MM_CC" CXX="$MM_CXX" MM_CPPFLAGS="$MM_CPPFLAGS"
cp Dist/FreeImage.h $MM_DEPS_PREFIX/include
cp Dist/libfreeimage.a $MM_DEPS_PREFIX/lib

pushd Wrapper/FreeImagePlus
# This one doesn't even come with a makefile(!)
cat > Makefile.clang <<'END_OF_MAKEFILE'
CC = cc-from-cmdline
CXX = cxx-from-cmdline
CFLAGS = -Os -fexceptions -fvisibility=hidden -DPIC -fno-common
CXXFLAGS = $(CFLAGS) -Wno-ctor-dtor-privacy
CPPFLAGS = -DNO_LCMS $(MM_CPPFLAGS) -I. -I../../Source
LIBTOOL = /usr/bin/libtool

TARGET = freeimageplus
STATICLIB = lib$(TARGET).a
HEADER = FreeImagePlus.h

SRCS = $(wildcard src/*.cpp)
MODULES = $(SRCS:.c=.o)
MODULES := $(MODULES:.cpp=.o)

all: dist

dist: FreeImagePlus
	cp *.a Dist
	cp $(HEADER) Dist

FreeImagePlus: $(STATICLIB)

$(STATICLIB): $(MODULES)
	$(LIBTOOL) -static -o $@ $(MODULES)

clean:
	rm -f Dist/$(STATICLIB) Dist/$(HEADER) $(MODULES) $(STATICLIB)
END_OF_MAKEFILE

make -f Makefile.clang $MM_PARALLELMAKEFLAG CC="$MM_CC" CXX="$MM_CXX" MM_CPPFLAGS="$MM_CPPFLAGS"
cp Dist/FreeImagePlus.h $MM_DEPS_PREFIX/include
cp Dist/libfreeimageplus.a $MM_DEPS_PREFIX/lib
popd # Wrapper

popd # FreeImage


#
# libdc1394-2
#

tar xzf ../downloads/libdc1394-2.2.1.tar.gz
pushd libdc1394-2.2.1

# Skip broken check for IOKit (patch configure, not configure.in, because
# autoreconf does not run -- looks like some m4 files are missing from the
# tarball)
patch -p2 <<'END_OF_PATCH'
--- a/libdc1394-2.2.1/configure 2013-01-28 02:47:43.000000000 +0000
+++ b/libdc1394-2.2.1/configure   2014-01-16 23:28:55.000000000 +0000
@@ -13609,13 +13609,13 @@

     ;;
 *-*-darwin*)
-    { $as_echo "$as_me:${as_lineno-$LINENO}: checking for IOMasterPort in -lIOKit" >&5
-$as_echo_n "checking for IOMasterPort in -lIOKit... " >&6; }
+    { $as_echo "$as_me:${as_lineno-$LINENO}: checking for IOMasterPort in -framework IOKit" >&5
+$as_echo_n "checking for IOMasterPort in -framework IOKit... " >&6; }
 if test "${ac_cv_lib_IOKit_IOMasterPort+set}" = set; then :
   $as_echo_n "(cached) " >&6
 else
   ac_check_lib_save_LIBS=$LIBS
-LIBS="-lIOKit  $LIBS"
+LIBS="-framework IOKit  $LIBS"
 cat confdefs.h - <<_ACEOF >conftest.$ac_ext
 /* end confdefs.h.  */

END_OF_PATCH

# Patch makefiles to fix library flag
patch -p2 <<'END_OF_PATCH'
--- a/libdc1394-2.2.1/dc1394/macosx/Makefile.am 2013-01-27 18:43:18.000000000 -0800
+++ b/libdc1394-2.2.1/dc1394/macosx/Makefile.am   2014-01-16 15:29:43.000000000 -0800
@@ -10,7 +10,7 @@

 AM_CFLAGS = -I..
 libdc1394_macosx_la_LDFLAGS = -framework CoreFoundation -framework Carbon
-libdc1394_macosx_la_LIBADD = -lIOKit
+libdc1394_macosx_la_LIBADD = -framework IOKit
 libdc1394_macosx_la_SOURCES =  \
        control.c \
        capture.c \
END_OF_PATCH
patch -p2 <<'END_OF_PATCH'
--- a/libdc1394-2.2.1/dc1394/macosx/Makefile.in 2013-01-27 18:47:45.000000000 -0800
+++ b/libdc1394-2.2.1/dc1394/macosx/Makefile.in   2014-01-16 15:31:28.000000000 -0800
@@ -265,7 +265,7 @@

 AM_CFLAGS = -I..
 libdc1394_macosx_la_LDFLAGS = -framework CoreFoundation -framework Carbon
-libdc1394_macosx_la_LIBADD = -lIOKit
+libdc1394_macosx_la_LIBADD = -framework IOKit
 libdc1394_macosx_la_SOURCES = \
        control.c \
        capture.c \
END_OF_PATCH

eval ./configure $MM_DEPS_CONFIGUREFLAGS --disable-shared --enable-static --with-pic --disable-sdltest --disable-examples "LIBS=\"-framework IOKit\""
make $MM_PARALLELMAKEFLAG
make install
popd


#
# OpenCV
#

############### TODO check deployment target and sdkroot; set cflags and cxxflags (esp. -v)

unzip -oq ../downloads/opencv-2.4.9.zip
pushd opencv-2.4.9
# OpenCV modules: highgui depends on imgproc; imgproc depends on core; OpenCVgrabber requires highgui and core
mkdir -p build-for-mm && cd build-for-mm
PKG_CONFIG_PATH=$MM_DEPS_PREFIX/lib/pkgconfig cmake \
-DBUILD_DOCS:BOOL=OFF \
-DBUILD_EXAMPLES:BOOL=OFF \
-DBUILD_PERF_TESTS:BOOL=OFF \
-DBUILD_SHARED_LIBS:BOOL=OFF \
-DBUILD_TESTS:BOOL=OFF \
-DBUILD_ZLIB:BOOL=OFF \
-DBUILD_opencv_core:BOOL=ON \
-DBUILD_opencv_imgproc:BOOL=ON \
-DBUILD_opencv_highgui:BOOL=ON \
-DBUILD_opencv_apps:BOOL=OFF \
-DBUILD_opencv_calib3d:BOOL=OFF \
-DBUILD_opencv_contrib:BOOL=OFF \
-DBUILD_opencv_features2d:BOOL=OFF \
-DBUILD_opencv_flann:BOOL=OFF \
-DBUILD_opencv_gpu:BOOL=OFF \
-DBUILD_opencv_java:BOOL=OFF \
-DBUILD_opencv_legacy:BOOL=OFF \
-DBUILD_opencv_ml:BOOL=OFF \
-DBUILD_opencv_nonfree:BOOL=OFF \
-DBUILD_opencv_objdetect:BOOL=OFF \
-DBUILD_opencv_ocl:BOOL=OFF \
-DBUILD_opencv_photo:BOOL=OFF \
-DBUILD_opencv_python:BOOL=OFF \
-DBUILD_opencv_stitching:BOOL=OFF \
-DBUILD_opencv_superres:BOOL=OFF \
-DBUILD_opencv_ts:BOOL=OFF \
-DBUILD_opencv_video:BOOL=OFF \
-DBUILD_opencv_videostab:BOOL=OFF \
-DBUILD_opencv_world:BOOL=OFF \
-DCMAKE_BUILD_TYPE:STRING=RelWithDebInfo \
-DCMAKE_C_COMPILER:STRING=/usr/bin/clang \
-DCMAKE_C_FLAGS:STRING="-v" \
-DCMAKE_CXX_COMPILER:STRING=/usr/bin/clang++ \
-DCMAKE_CXX_FLAGS:STRING="-v" \
-DCMAKE_INSTALL_PREFIX="$MM_DEPS_PREFIX" \
-DCMAKE_OSX_ARCHITECTURES:STRING="i386;x86_64" \
-DCMAKE_OSX_DEPLOYMENT_TARGET:STRING=10.5 \
-DCMAKE_OSX_SYSROOT:STRING=$SDKROOT \
-DCMAKE_VERBOSE_MAKEFILE:BOOL=ON \
-DENABLE_PRECOMPILED_HEADERS:BOOL=ON \
-DWITH_1394:BOOL=ON \
-DWITH_CUDA:BOOL=OFF \
-DWITH_EIGEN:BOOL=OFF \
-DWITH_FFMPEG:BOOL=ON \
-DWITH_JASPER:BOOL=OFF \
-DWITH_JPEG:BOOL=OFF \
-DWITH_OPENCL:BOOL=OFF \
-DWITH_OPENEXR:BOOL=OFF \
-DWITH_OPENMP:BOOL=OFF \
-DWITH_OPENNI:BOOL=OFF \
-DWITH_PNG:BOOL=OFF \
-DWITH_TIFF:BOOL=OFF \
-DZLIB_INCLUDE_DIR:STRING=$SDKROOT/usr/include \
-DZLIB_LIBRARY:STRING=$SDKROOT/usr/lib/libz.dylib \
..
make $MM_PARALLELMAKEFLAG
make install
popd

echo "Finished building dependencies"
