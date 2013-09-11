pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_PiezoJenadDrive.la
libmmgr_dal_PiezoJenadDrive_la_SOURCES = Piezosystem_dDrive.cpp \
													  Piezosystem_dDrive.h \
													  devicelist.h \
													  devicelist.cpp
libmmgr_dal_PiezoJenadDrive_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_PiezoJenadDrive_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

EXTRA_DIST =  PiezoJenadDrive.vcproj license.txt
