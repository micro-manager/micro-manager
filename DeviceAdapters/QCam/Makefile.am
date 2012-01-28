pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_QCam.la
libmmgr_dal_QCam_la_SOURCES = QICamera.cpp QICamera.h DeviceEvents.h
libmmgr_dal_QCam_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_QCam_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) -framework QCam

EXTRA_DIST = QCam.vcproj
