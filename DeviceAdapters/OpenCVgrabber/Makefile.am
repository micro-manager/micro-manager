pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive -fweak $(OPENCV_CPPFLAGS)
pkglib_LTLIBRARIES = libmmgr_dal_OpenCVgrabber.la
libmmgr_dal_OpenCVgrabber_la_SOURCES = OpenCVgrabber.cpp OpenCVgrabber.h ../../MMDevice/MMDevice.h
libmmgr_dal_OpenCVgrabber_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(OPENCV_LDFLAGS) \
	$(QUICKTIMEFRAMEWORKS) $(OPENCV_XLDFLAGS) -ldc1394
libmmgr_dal_OpenCVgrabber_la_LIBADD = $(MMDEVAPI_LIBADD)

