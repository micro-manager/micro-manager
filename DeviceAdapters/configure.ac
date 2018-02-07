
AC_PREREQ([2.64])
AC_INIT([Micro-Manager], [1.4], [info@micro-manager.org])
AC_CONFIG_MACRO_DIR([../m4])
AC_CONFIG_SRCDIR([DemoCamera/DemoCamera.cpp])
AC_CANONICAL_HOST
AM_INIT_AUTOMAKE([foreign 1.11])
LT_PREREQ([2.2.6])
LT_INIT([disable-static])

AC_PROG_CXX([clang++ llvm-g++ g++ c++])
AC_PROG_CC([clang llvm-gcc gcc cc])

MM_GMOCK([$srcdir/../testing/gmock], [\$(top_srcdir)/../testing/gmock])
AM_CONDITIONAL([BUILD_CPP_TESTS], [test "x$have_gmock" = xyes])

# TODO: Following should decide whether adapters depending on BOOST get build
AX_BOOST_BASE([1.48.0])
AX_BOOST_THREAD
AX_BOOST_ASIO
AX_BOOST_SYSTEM
AX_BOOST_CHRONO
AX_BOOST_DATE_TIME

# Find Micro-Manager headers
micromanager_path=${ac_pwd}/..
MMDEVAPI_CXXFLAGS="-I${micromanager_path}/MMDevice ${BOOST_CPPFLAGS}"
AC_SUBST(MMDEVAPI_CXXFLAGS)

# Find Micro-Manager static device library
MMDEVAPI_LIBADD="${micromanager_path}/MMDevice/libMMDevice.la"
AC_SUBST(MMDEVAPI_LIBADD)

# Apply appropriate libtool options for the Micro-Manager device API
MMDEVAPI_LDFLAGS="-module -avoid-version -shrext \"\$(MMSUFFIX)\""
AC_SUBST(MMDEVAPI_LDFLAGS)

# Location of third party public files
thirdpartypublic="${micromanager_path}/../3rdpartypublic"


MM_INSTALL_DIRS


# libusb 0.1 or libusb-compat
MM_ARG_WITH_OPTIONAL_LIB([libusb 0.1 or libusb-compat], [libusb-0-1], [LIBUSB_0_1])
AS_IF([test "x$want_libusb_0_1" != xno],
[
   MM_LIB_USB_0_1([$LIBUSB_0_1_PREFIX],
   [
      use_libusb_0_1=yes
   ],
   [
      use_libusb_0_1=no
      AS_IF([test "x$want_libusb_0_1" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([libusb 0.1 or libusb-compat], [libusb-0-1])])
   ])
],
[use_libusb_0_1=no])


AM_CONDITIONAL([BUILD_USBMANAGER], [test "x$use_libusb_0_1" = xyes])


# HIDAPI
MM_ARG_WITH_OPTIONAL_LIB([HIDAPI], [hidapi], [HIDAPI])
AS_IF([test "x$want_hidapi" != xno],
[
   MM_LIB_HIDAPI([$HIDAPI_PREFIX],
   [
      use_hidapi=yes
   ],
   [
      use_hidapi=no
      AS_IF([test "x$want_hidapi" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([HIDAPI], [hidapi])])
   ])
],
[use_hidapi=no])


AM_CONDITIONAL([BUILD_HID], [test "x$use_hidapi" = xyes])


# Check for libmodbus
MM_ARG_WITH_OPTIONAL_LIB([libmodbus], [modbus], [MODBUS])
AS_IF([test "x$want_modbus" != xno],
[
   MM_LIB_MODBUS([$MODBUS_PREFIX],
   [
      use_modbus=yes
   ],
   [
      use_modbus=no
      AS_IF([test "x$want_modbus" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([libmodbus], [modbus])])
   ])
],
[use_modbus=no])

AM_CONDITIONAL([BUILD_MODBUS], [test "x$use_modbus" = xyes])


# Only build PVCAM if header files present.
AC_MSG_CHECKING(for Pvcam)
for dir in /usr/include/pvcam /Library/Frameworks/PVCAM.framework/Headers
do
   if test -f "$dir/pvcam.h"; then
      PVCAMINCDIR="$dir"
      break
   fi
done
AM_CONDITIONAL([BUILD_PVCAM], [test -n "$PVCAMINCDIR"])
AM_COND_IF([BUILD_PVCAM],
[
   AC_MSG_RESULT([found])
],
[
   AC_MSG_RESULT([not found])
])

# Same for QCam
AC_MSG_CHECKING(for QCam)
AM_CONDITIONAL([BUILD_QCAM],[test -f "/Library/Frameworks/QCam.framework/Headers/QCamApi.h"])
if test -f "/Library/Frameworks/QCam.framework/Headers/QCamApi.h" ; then
   AC_MSG_RESULT([found])
else
   AC_MSG_RESULT([not found])
fi


# Same for Scion
AC_MSG_CHECKING(for Scion)
AM_CONDITIONAL([BUILD_SCION],[test -f "/Library/Frameworks/sfwlib.framework/Headers/sfwlib.h"])
if test -f "/Library/Frameworks/sfwlib.framework/Headers/sfwlib.h"; then
   AC_MSG_RESULT([found])
else
   AC_MSG_RESULT([not found])
fi

# Same for Diagnostic Spot
AC_MSG_CHECKING(for Diagnostic Spot)
AM_CONDITIONAL([BUILD_SPOT],[test -f "/Library/Frameworks/SpotCam.framework/Headers/SpotCam.h"])
if test -f "/Library/Frameworks/SpotCam.framework/Headers/SpotCam.h"; then
   AC_MSG_RESULT([found])
else
   AC_MSG_RESULT([not found])
fi


# libdc1394 2.x
MM_ARG_WITH_OPTIONAL_LIB([libdc1394], [libdc1394], [LIBDC1394])
AS_IF([test "x$want_libdc1394" != xno],
[
   MM_LIB_DC1394([$LIBDC1394_PREFIX],
   [
      use_libdc1394=yes

      # The libdc1394 OS X header changed in version 2.2.2
      AC_CHECK_HEADER([dc1394/macosx.h],
                      [AC_DEFINE([HAVE_LIBDC1394_2_2_2_MACOSX_H], [1],
                                 [Define if dc1394/macosx.h exists])],
                      [],
                      [[#include <dc1394/dc1394.h>]])
   ],
   [
      use_libdc1394=no
      AS_IF([test "x$want_libdc1394" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([libdc1394], [libdc1394])])
   ])
],
[use_libdc1394=no])


# TODO These BUILD_x conditionals should not be associated with particular
# libraries; they might depend on multiple libraries. Should collect them at
# the end of this file
AM_CONDITIONAL([BUILD_DC1394], [test "x$use_libdc1394" = xyes])

build_IIDC=$use_libdc1394
if test -z "$BOOST_CHRONO_LIB"; then
   build_IIDC=no
fi
AM_CONDITIONAL([BUILD_IIDC], [test "x$build_IIDC" = xyes])


# Andor cameras on linux 
AC_SEARCH_LIBS([SaveAsSif], [andor], [ANDORFOUND=yes], [ANDORFOUND=no])
AM_CONDITIONAL([BUILD_ANDOR], [test "x$ANDORFOUND" = xyes])

# Andor sCMOS cameras on Linux
AC_MSG_CHECKING([for Andor sCMOS cameras])
AM_CONDITIONAL([BUILD_ANDORSDK3], [test -f "/usr/local/include/atcore.h"])
if test -f "/usr/local/include/atcore.h"; then
   AC_MSG_RESULT([found])
   if test -z "$LIBATCORE"; then
      case $host in
         *-*-linux*)
            LIBATCORE="-latcore"
            LIBATUTILITY="-latutility"
            ;;
      esac
   fi
else
   AC_MSG_RESULT([not found])
fi

AC_SUBST(LIBATCORE)
AC_SUBST(LIBATUTILITY)

# Andor laser combiner on linux 
AC_MSG_CHECKING(for Andor laser combiner)
AM_CONDITIONAL([BUILD_ANDORLASERCOMBINER],[test -f "/usr/local/include/ALC_REV.h"])
if test -f "/usr/local/include/ALC_REV.h" ; then
   AC_MSG_RESULT([found])
else
   AC_MSG_RESULT([not found])
fi

# Sensicam on linux 
AC_MSG_CHECKING(for Sensicam)
AM_CONDITIONAL([BUILD_SENSICAM],[test -f "/usr/local/include/sensicam.h"])
if test -f "/usr/local/include/sensicam.h"; then
   AC_MSG_RESULT([found])
else
   AC_MSG_RESULT([not found])
fi



# check for IDS_uEye header file and library
AC_MSG_CHECKING(for IDS_uEye header file and library)
AC_CHECK_HEADERS(ueye.h,
	[AC_CHECK_LIB(ueye_api,is_GetDLLVersion,IDSUEYE_PRESENT=true,IDSUEYE_PRESENT=false)],
	IDSUEYE_PRESENT=false)
AM_CONDITIONAL(BUILD_IDSUEYE, $IDSUEYE_PRESENT)

if [$IDSUEYE_PRESENT]; then
   AC_MSG_NOTICE([IDS_uEye present])
else
   AC_MSG_NOTICE([IDS_uEye not present])
fi


MM_ARG_WITH_OPTIONAL_LIB([libgphoto2], [gphoto2], [GPHOTO2])
AS_IF([test "x$want_gphoto2" != xno],
[
   MM_LIB_GPHOTO2([$GPHOTO2_PREFIX],
   [
      use_gphoto2=yes
   ],
   [
      use_gphoto2=no
      AS_IF([test "x$want_gphoto2" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([libgphoto2], [gphoto2])])
   ])
],
[use_gphoto2=no])


MM_ARG_WITH_OPTIONAL_LIB([FreeImagePlus], [freeimageplus], [FREEIMAGEPLUS])
AS_IF([test "x$want_freeimageplus" != xno],
[
   MM_LIB_FREEIMAGEPLUS([$FREEIMAGEPLUS_PREFIX],
   [
      use_freeimageplus=yes
   ],
   [
      use_freeimageplus=no
      AS_IF([test "x$want_freeimageplus" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([FreeImagePlus], [freeimageplus])])
   ])
],
[use_freeimageplus=no])


build_simplecam=no
test "x$use_gphoto2" = xyes && {
   test "x$use_freeimageplus" = xyes && build_simplecam=yes; }
AM_CONDITIONAL([BUILD_SIMPLECAM], [test "x$build_simplecam" = xyes])


# Video4linux
AC_MSG_CHECKING(for Video4linux2)
AM_CONDITIONAL([BUILD_V4L2],[test -f "/usr/include/linux/videodev2.h"])
if test -f "/usr/include/linux/videodev2.h"; then
   AC_MSG_RESULT([found])
else
   AC_MSG_RESULT([not found])
fi

# ITC on linux 
AC_MSG_CHECKING(for ITC)
AM_CONDITIONAL([BUILD_ITC],[test -f "/usr/local/include/ITC18.h"])
if test -f "/usr/local/include/ITC18.h" ; then
   AC_MSG_RESULT([found])
else
   AC_MSG_RESULT([not found])
fi


# MessagePack
MM_ARG_WITH_OPTIONAL_LIB([MessagePack], [msgpack], [MSGPACK])
AS_IF([test "x$want_msgpack" != xno],
[
   MM_LIB_MSGPACK([$MSGPACK_PREFIX],
   [
      use_msgpack=yes
   ],
   [
      use_msgpack=no
      AS_IF([test "x$want_msgpack" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([MSGPACK], [msgpack])])
   ])
],
[use_msgpack=no])


AM_CONDITIONAL([BUILD_SEQUENCER_TEST], [test "x$use_msgpack" = xyes])


# OpenCV
MM_ARG_WITH_OPTIONAL_LIB([OpenCV], [opencv], [OPENCV])
AS_IF([test "x$want_opencv" != xno],
[
   MM_LIB_OPENCV([$OPENCV_PREFIX],
   [
      use_opencv=yes
   ],
   [
      use_opencv=no
      AS_IF([test "x$want_opencv" = xyes],
            [MM_MSG_OPTIONAL_LIB_FAILURE([OpenCV], [opencv])])
   ])
],
[use_opencv=no])


AM_CONDITIONAL([BUILD_OPENCV], [test "x$use_opencv" = xyes])


# Only build ... when the code is there
AM_CONDITIONAL([BUILD_TEST],[test -f "Test/Test.h"])
if test -f "Test/Test.h"; then
   TEST=Test
   TESTMAKEFILE="Test/Makefile"
else
   TEST=
   TESTMAKEFILE=
fi
AC_SUBST(TEST)

# Checks for typedefs, structures, and compiler characteristics.
AC_HEADER_STDBOOL
AC_C_CONST
AC_C_INLINE


# On the Mac we need some frameworks that can be in the way on Linux
case $host in 
   *apple-darwin*)
      AC_MSG_CHECKING(pvcam linker flags)
      PVCAMFRAMEWORKS="-framework PVCAM"
      AC_MSG_RESULT($PVCAMFRAMEWORKS)
      AC_MSG_CHECKING(diagnostic spot linker flags)
      SPOTFRAMEWORKS="-framework SpotCam"
      AC_MSG_RESULT($SPOTFRAMEWORKS)
      AC_MSG_CHECKING(Serial adapter linker flags)
      SERIALFRAMEWORKS="-framework CoreFoundation -framework IOKit"
      AC_MSG_RESULT($SERIALFRAMEWORKS)
      AC_MSG_CHECKING(QuickTime linker flags)
      QUICKTIMEFRAMEWORKS="-framework QuickTime -framework QTKit"
      AC_MSG_RESULT($QUICKTIMEFRAMEWORKS)
      ;;
   *linux*)
      AC_MSG_CHECKING(pvcam linker flags)
      PVCAMFRAMEWORKS="-lpvcam"
      ;;
esac
AC_SUBST(PVCAMFRAMEWORKS)
AC_SUBST(SPOTFRAMEWORKS)
AC_SUBST(SERIALFRAMEWORKS)
AC_SUBST(QUICKTIMEFRAMEWORKS)

# Micro-Manager libraries have a prefix & suffix to make them unique.  These are defined here:
case $host in
   *-*-linux*)
   MMSUFFIX=".so.0"
   MMPREFIX="libmmgr_dal_"
   ;;
esac
if test -z "$MMSUFFIX"; then
  MMSUFFIX=""
fi
if test -z "$MMPREFIX"; then
  MMPREFIX="mmgr_dal_"
fi

AC_MSG_CHECKING(library suffix)
AC_MSG_RESULT($MMSUFFIX)
AC_SUBST(MMSUFFIX)
AC_MSG_CHECKING(library prefix)
AC_MSG_RESULT($MMPREFIX)
AC_SUBST(MMPREFIX)


# Checks for library functions.
AC_HEADER_STDC
AC_CHECK_FUNCS([memset])


# Please keep the list of device adapter directories in ASCII-lexical order,
# with an indent of 3 spaces (no tabs)! (Just pass through sort(1).)
# This is the list of subdirectories containing a Makefile.am.
m4_define([device_adapter_dirs], [m4_strip([
   AAAOTF
   AOTF
   ASIFW1000
   ASIStage
   ASITiger
   ASIWPTR
   Aladdin
   Andor
   AndorLaserCombiner
   AndorSDK3
   Aquinas
   Arduino
   BlueboxOptics_niji
   CARVII
   CNCMicroscope
   CNCMicroscope/ArduinoNeoPixelShutter
   CNCMicroscope/RAMPSStage
   CSUW1
   Cobolt
   CoherentCube
   CoherentOBIS
   CoherentScientificRemote
   Conix
   Corvus
   DTOpenLayer
   DemoCamera
   Diskovery
   FakeCamera
   FocalPoint
   FreeSerialPort
   HIDManager
   HydraLMT200
   IDS_uEye
   IIDC
   ITC18
   ImageProcessorChain
   K8055
   K8061
   LeicaDMI
   LeicaDMR
   LeicaDMSTC
   Ludl
   LudlLow
   LumencorSpectra
   MP285
   MaestroServo
   Marzhauser
   Marzhauser-LStep
   MarzhauserLStepOld
   MicroPoint
   Modbus
   Motic_mac
   Neos
   NewportCONEX
   NewportSMC
   Nikon
   NikonTE2000
   OVP_ECS2
   Omicron
   OpenCVgrabber
   Oxxius
   PI
   PI_GCS
   PI_GCS_2
   PVCAM
   ParallelPort
   Pecon
   Piezosystem_30DV50
   Piezosystem_NV120_1
   Piezosystem_NV40_1
   Piezosystem_NV40_3
   Piezosystem_dDrive
   PrecisExcite
   PrincetonInstruments
   Prior
   PriorLegacy
   QCam
   Sapphire
   Scientifica
   ScionCam
   Sensicam
   SequenceTester
   SerialManager
   SimpleAutofocus
   SimpleCam
   SmarActHCU-3D
   SpectralLMM5
   Spot
   SutterLambda
   SutterStage
   Thorlabs
   ThorlabsDCxxxx
   ThorlabsFilterWheel
   ThorlabsSC10
   Tofra
   TriggerScope
   USBManager
   UserDefinedSerial
   UserDefinedSerial/unittest
   Utilities
   VariLC
   Video4Linux
   Vincent
   Vortran
   WieneckeSinske
   XCite120PC_Exacte
   XCiteLed
   XCiteXT600
   XLight
   Xcite
   Yokogawa
   Zaber
   ZeissCAN
   ZeissCAN29
   dc1394
   kdv
   nPoint
   pgFocus
])])

AC_CONFIG_FILES(Makefile m4_map_args_w(device_adapter_dirs, [], [/Makefile], [ ]))

AC_OUTPUT


# Show a summary of the configuration
echo ""
echo "m4_text_box([DeviceAdapters configuration])"
echo ""
echo "Note: This is an incomplete list currently under development."
echo ""
echo "m4_text_wrap([$use_freeimageplus],
                   [                                            ],
                   [    Build with FreeImagePlus:               ])"
echo "m4_text_wrap([$use_hidapi],
                   [                                            ],
                   [    Build with HIDAPI:                      ])"
echo "m4_text_wrap([$use_modbus],
                   [                                            ],
                   [    Build with Modbus:                      ])"
echo "m4_text_wrap([$use_libdc1394],
                   [                                            ],
                   [    Build with libdc1394:                   ])"
echo "m4_text_wrap([$use_gphoto2],
                   [                                            ],
                   [    Build with libgphoto2:                  ])"
echo "m4_text_wrap([$use_libusb_0_1],
                   [                                            ],
                   [    Build with libusb 0.1 (libusb-compat):  ])"
echo "m4_text_wrap([$use_opencv],
                   [                                            ],
                   [    Build with OpenCV:                      ])"
echo ""
