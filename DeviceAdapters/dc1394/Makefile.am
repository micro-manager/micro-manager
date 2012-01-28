pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive 
pkglib_LTLIBRARIES = libmmgr_dal_dc1394.la
libmmgr_dal_dc1394_la_SOURCES = dc1394.cpp dc1394.h 
##TODO: find the library in configure
libmmgr_dal_dc1394_la_LIBADD = $(MMDEVAPI_LIBADD) $(LIBDC1394)
libmmgr_dal_dc1394_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = license.txt
