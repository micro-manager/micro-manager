pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_PVCAM.la
libmmgr_dal_PVCAM_la_SOURCES = PVCAM.cpp PVCAM.h PVCAMUtils.cpp PVCAMUtils.h PVCAMUniversal.cpp
libmmgr_dal_PVCAM_la_LIBADD = $(MMDEVAPI_LIBADD) $(LIBACE)
libmmgr_dal_PVCAM_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(PVCAMFRAMEWORKS)

EXTRA_DIST = PVCAM.vcproj license.txt
