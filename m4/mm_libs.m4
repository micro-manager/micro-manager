
# Author: Mark Tsuchida
# Copyright: University of California, San Francisco, 2014
# License: BSD

#
# Macros to check for particular libraries, our style.
#

# MM_LIB_DC1394([dc1394-prefix], [action-if-found], [action-if-not-found])
#
# Checks for an appropriate version of libdc1394 2.x.
#
# Defines precious variables LIBDC1394_CPPFLAGS, LIBDC1394_CFLAGS,
# LIBDC1394_LDFLAGS, and LIBDC1394_LIBS.
#
AC_DEFUN([MM_LIB_DC1394], [
   MM_LIB_WITH_PKG_CONFIG([LIBDC1394], [libdc1394], [libdc1394-2], [],
      [$1], [-ldc1394], [dc1394/dc1394.h], [dc1394_capture_is_frame_corrupt],
      [$2], [$3])
])


AC_DEFUN([MM_LIB_FREEIMAGEPLUS], [
   AC_LANG_PUSH([C++])
   MM_LIB_SIMPLE_CXX([FREEIMAGEPLUS], [FreeImagePlus],
      [$1], [-lfreeimageplus -lfreeimage], [FreeImagePlus.h],
      dnl No particular reason for the choice of function to check for.
      [AC_LANG_PROGRAM([[#include <FreeImagePlus.h>]],
                       [[fipImage fimg;]])],
      [$2], [$3])
   AC_LANG_POP([C++])
])


AC_DEFUN([MM_LIB_GPHOTO2], [
   MM_LIB_WITH_PKG_CONFIG([GPHOTO2], [libgphoto2], [libgphoto2 >= 2.5.1], [],
      [$1], [-lgphoto2], [gphoto2/gphoto2.h], [gp_camera_capture],
      [$2], [$3])
])


# Check for HIDAPI library
#
# MM_LIB_HIDAPI([HIDAPI prefix], [action-if-found], [action-if-not-found])
#
# Defines precious variables HIDAPI_CPPFLAGS, HIDAPI_CFLAGS, HIDAPI_LDFLAGS,
# HIDAPI_LIS.
#
AC_DEFUN([MM_LIB_HIDAPI], [
   MM_LIB_WITH_PKG_CONFIG([HIDAPI], [HIDAPI], [hidapi], [],
      [$1], [-lhidapi], [hidapi/hidapi.h], [hid_init],
      [$2], [$3])
])


# MM_LIB_MSGPACK([msgpack-prefix], [action-if-found], [action-if-not-found])
AC_DEFUN([MM_LIB_MSGPACK], [
   AC_LANG_PUSH([C++])
   MM_LIB_SIMPLE_CXX([MSGPACK], [MessagePack],
   [$1], [-lmsgpackc], [msgpack.hpp],
   [AC_LANG_PROGRAM([[#include <msgpack.hpp>]],
                    [[msgpack::sbuffer buf;]])],
   [$2], [$3])
   AC_LANG_POP([C++])
])


# Check for OpenCV video capture
#
# MM_LIB_OPENCV([OpenCV prefix], [action-if-found], [action-if-not-found])
#
# Defines precious variables OPENCV_CPPFLAGS, OPENCV_CFLAGS, OPENCV_LDFLAGS,
# OPENCV_LIBS.
#
AC_DEFUN([MM_LIB_OPENCV], [
   MM_LIB_WITH_PKG_CONFIG([OPENCV], [OpenCV], [opencv], [],
      [$1], [-lopencv_highgui -lopencv_imgproc -lopencv_core],
      [opencv/highgui.h], [cvGetCaptureProperty],
      [$2], [$3])
])


# MM_LIB_USB_0_1([libusb-prefix], [action-if-found], [action-if-not-found])
#
# Checks for libusb 0.1 (or libusb-1.0-based libusb-compat)
#
# Defines variables LIBUSB_0_1_CPPFLAGS, LIBUSB_0_1_CFLAGS, LIBUSB_0_1_LDFLAGS,
# and LIBUSB_0_1_LIBS.
#
AC_DEFUN([MM_LIB_USB_0_1], [
   MM_LIB_WITH_PKG_CONFIG([LIBUSB_0_1], [libusb 0.1 or libusb-compat], [libusb], [],
      [$1], [-lusb], [usb.h], [usb_init],
      [$2], [$3])
])
