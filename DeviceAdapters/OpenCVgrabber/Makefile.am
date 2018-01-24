

AM_CPPFLAGS = $(OPENCV_CPPFLAGS)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(OPENCV_CFLAGS)
AM_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(OPENCV_LDFLAGS)

deviceadapter_LTLIBRARIES = libmmgr_dal_OpenCVgrabber.la

libmmgr_dal_OpenCVgrabber_la_SOURCES = DeviceEnumerator.cpp DeviceEnumerator.h OpenCVgrabber.cpp OpenCVgrabber.h
libmmgr_dal_OpenCVgrabber_la_LIBADD = $(MMDEVAPI_LIBADD) $(OPENCV_LIBS)
