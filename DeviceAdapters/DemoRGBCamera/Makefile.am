AM_CXXFLAGS = -fpermissive -fweak
lib_LTLIBRARIES = libmmgr_dal_DemoRGBCamera.la
libmmgr_dal_DemoRGBCamera_la_SOURCES = DemoRGBCamera.cpp DemoRGBCamera.h RGBSignalGenerator.cpp
libmmgr_dal_DemoRGBCamera_la_LDFLAGS = -module 
libmmgr_dal_DemoRGBCamera_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a $(LIBACE)

install:
	cp .libs/libmmgr_dal_DemoRGBCamera.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_DemoRGBCamera$(MMSUFFIX)

EXTRA_DIST = DemoRGBCamera.vcproj license.txt
