pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_NikonTE2000.la
libmmgr_dal_NikonTE2000_la_SOURCES = NikonTE2000.cpp NikonTE2000.h TEHub.cpp TEHub.h
libmmgr_dal_NikonTE2000_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_NikonTE2000_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = NikonTE2000.vcproj NikonTE2000_vc8.sln license.txt
