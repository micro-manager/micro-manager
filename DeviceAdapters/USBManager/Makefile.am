

AM_CPPFLAGS = $(LIBUSB_0_1_CPPFLAGS)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(LIBUSB_0_1_CFLAGS)
AM_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(LIBUSB_0_1_LDFLAGS)

deviceadapter_LTLIBRARIES = libmmgr_dal_USBManager.la
libmmgr_dal_USBManager_la_SOURCES = USBManager.cpp USBManager.h 
libmmgr_dal_USBManager_la_LIBADD = $(MMDEVAPI_LIBADD) $(LIBUSB_0_1_LIBS)

EXTRA_DIST = license.txt
