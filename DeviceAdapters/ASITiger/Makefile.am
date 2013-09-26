pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_ASITiger.la
libmmgr_dal_ASITiger_la_SOURCES = \
	ASIClocked.cpp \
	ASIClocked.h \
	ASICRISP.cpp \
	ASICRISP.h \
	ASIDevice.cpp \
	ASIDevice.h \
	ASIFWheel.cpp \
	ASIFWheel.h \
	ASIHub.cpp \
	ASIHub.h \
	ASIMMirror.cpp \
	ASIMMirror.h \
	ASIPiezo.cpp \
	ASIPiezo.h \
	ASITigerComm.cpp \
	ASITigerComm.h \
	ASITiger.cpp \
	ASITiger.h \
	ASIXYStage.cpp \
	ASIXYStage.h \
	ASIZStage.cpp \
	ASIZStage.h
libmmgr_dal_ASITiger_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_ASITiger_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST = ASITiger.vcproj license.txt
