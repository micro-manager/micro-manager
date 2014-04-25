
AM_CPPFLAGS = -F/Library/Frameworks
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_PrincetonInstruments.la
libmmgr_dal_PrincetonInstruments_la_SOURCES = PVCAM.cpp PVCAM.h PVCAMUtils.cpp PVCAMUtils.h PVCAMUniversal.cpp
libmmgr_dal_PrincetonInstruments_la_LIBADD = $(MMDEVAPI_LIBADD) $(LIBACE)
libmmgr_dal_PrincetonInstruments_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(PVCAMFRAMEWORKS)

EXTRA_DIST = PVCAM.vcproj license.txt
