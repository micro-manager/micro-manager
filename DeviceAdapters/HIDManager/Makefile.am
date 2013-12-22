pkglibdir = $(DEVICEADAPTERPATH)
AM_CFLAGS = -I$(HIDAPIINCDIR)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -I$(HIDAPIINCDIR) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_HIDManager.la
libmmgr_dal_HIDManager_la_SOURCES = HIDManager.cpp HIDManager.h $(HIDAPICDIR)/hid.c
libmmgr_dal_HIDManager_la_LIBADD = $(MMDEVAPI_LIBADD) $(SERIALFRAMEWORKS)
libmmgr_dal_HIDManager_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = license.txt
