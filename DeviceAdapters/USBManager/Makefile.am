pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_USBManager.la
libmmgr_dal_USBManager_la_SOURCES = USBManager.cpp USBManager.h 
libmmgr_dal_USBManager_la_LIBADD = $(MMDEVAPI_LIBADD) $(LIBUSB)
libmmgr_dal_USBManager_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = license.txt
