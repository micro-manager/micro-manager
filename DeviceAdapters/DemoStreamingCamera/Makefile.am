AM_CXXFLAGS = -fpermissive -fweak
lib_LTLIBRARIES = libmmgr_dal_DemoStreamingCamera.la
libmmgr_dal_DemoStreamingCamera_la_SOURCES = DemoStreamingCamera.cpp DemoStreamingCamera.h NoiseProcessor.cpp SignalGenerator.cpp
libmmgr_dal_DemoStreamingCamera_la_LDFLAGS = -module 
libmmgr_dal_DemoStreamingCamera_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a $(LIBACE)

install:
	cp .libs/libmmgr_dal_DemoStreamingCamera.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_DemoStreamingCamera$(MMSUFFIX)

EXTRA_DIST = DemoStreamingCamera.vcproj license.txt
