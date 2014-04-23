

AM_CPPFLAGS = $(HIDAPI_CPPFLAGS)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(HIDAPI_CFLAGS)
AM_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(HIDAPI_LDFLAGS)

deviceadapter_LTLIBRARIES = libmmgr_dal_HIDManager.la
libmmgr_dal_HIDManager_la_SOURCES = HIDManager.cpp HIDManager.h
libmmgr_dal_HIDManager_la_LIBADD = $(MMDEVAPI_LIBADD) $(HIDAPI_LIBS)

EXTRA_DIST = license.txt
