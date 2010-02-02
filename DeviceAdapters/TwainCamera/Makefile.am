AM_CXXFLAGS = -fpermissive -fweak
lib_LTLIBRARIES = libmmgr_dal_TwainRGBCamera.la
libmmgr_dal_TwainRGBCamera_la_SOURCES = TwainRGBCamera.cpp TwainRGBCamera.h RGBSignalGenerator.cpp
libmmgr_dal_TwainRGBCamera_la_LDFLAGS = -module 
libmmgr_dal_TwainRGBCamera_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a $(LIBACE)

install:
	cp .libs/libmmgr_dal_TwainRGBCamera.so $(DEVICEADAPTERPATH)/libmmgr_dal_TwainRGBCamera$(MMSUFFIX)

EXTRA_DIST = TwainRGBCamera.vcproj license.txt
