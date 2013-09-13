pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_ASITiger.la
libmmgr_dal_ASITiger_la_SOURCES = ASITiger.cpp ASIDevice.cpp ASIFSlider.cpp ASIFWheel.cpp ASIHub.cpp ASIMMirror.cpp ASIPiezo.cpp ASITigerComm.cpp ASIXYStage.cpp ASIZSTage.cpp ASITiger.h
libmmgr_dal_ASITiger_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_ASITiger_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = ASITiger.vcproj license.txt
