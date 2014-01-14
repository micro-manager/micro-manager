pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_ASITiger.la
libmmgr_dal_ASITiger_la_SOURCES = \
	ASICRISP.cpp \
	ASICRISP.h \
	ASIClocked.cpp \
	ASIClocked.h \
	ASIDevice.cpp \
	ASIDevice.h \
	ASIFWheel.cpp \
	ASIFWheel.h \
	ASIHub.cpp \
	ASIHub.h \
	ASIPiezo.cpp \
	ASIPiezo.h \
	ASIScanner.cpp \
	ASIScanner.h \
	ASITiger.cpp \
	ASITiger.h \
	ASITigerComm.cpp \
	ASITigerComm.h \
	ASIXYStage.cpp \
	ASIXYStage.h \
	ASIZStage.cpp \
	ASIZStage.h
libmmgr_dal_ASITiger_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_ASITiger_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = ASITiger.vcproj license.txt
