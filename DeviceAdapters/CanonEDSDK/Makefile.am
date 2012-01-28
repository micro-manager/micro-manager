pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive -fweak
pkglib_LTLIBRARIES = libmmgr_dal_CanonEDSDK.la
libmmgr_dal_CanonEDSDK_la_SOURCES = CanonEDSDK.cpp CanonEDSDK.h ../../MMDevice/MMDevice.h
libmmgr_dal_CanonEDSDK_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) -framework EDSDK
libmmgr_dal_CanonEDSDK_la_LIBADD = $(MMDEVAPI_LIBADD)
