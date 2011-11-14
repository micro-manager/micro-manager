AM_CXXFLAGS = -fpermissive -fweak $(OPENCV_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_OpenCVgrabber.la
libmmgr_dal_OpenCVgrabber_la_SOURCES = OpenCVgrabber.cpp OpenCVgrabber.h ../../MMDevice/MMDevice.h
libmmgr_dal_OpenCVgrabber_la_LDFLAGS = -module $(OPENCV_LDFLAGS)
libmmgr_dal_OpenCVgrabber_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install: libmmgr_dal_OpenCVgrabber.la
	cp .libs/libmmgr_dal_OpenCVgrabber.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_OpenCVgrabber$(MMSUFFIX)
