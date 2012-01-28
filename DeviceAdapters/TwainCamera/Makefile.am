pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive -fweak
pkglib_LTLIBRARIES = libmmgr_dal_TwainRGBCamera.la
libmmgr_dal_TwainRGBCamera_la_SOURCES = TwainRGBCamera.cpp TwainRGBCamera.h RGBSignalGenerator.cpp
libmmgr_dal_TwainRGBCamera_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) 
libmmgr_dal_TwainRGBCamera_la_LIBADD = $(MMDEVAPI_LIBADD) $(LIBACE)

EXTRA_DIST = TwainRGBCamera.vcproj license.txt
