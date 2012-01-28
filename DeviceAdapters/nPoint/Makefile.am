pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_nPoint.la
libmmgr_dal_nPoint_la_SOURCES = nPC400.cpp nPC400.h nPC400Ctrl.cpp nPC400Ctrl.h nPC400Channel.cpp nPC400Channel.h
libmmgr_dal_nPoint_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_nPoint_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST =  nPC400.vcproj  nPC400.sln
