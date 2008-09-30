AM_CXXFLAGS = -fpermissive -fweak
lib_LTLIBRARIES = DemoStreamingCamera.la
DemoStreamingCamera_la_SOURCES = DemoStreamingCamera.cpp DemoStreamingCamera.h NoiseProcessor.cpp SignalGenerator.cpp
DemoStreamingCamera_la_LDFLAGS = -module 
DemoStreamingCamera_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a $(LIBACE)

install:
	cp .libs/DemoStreamingCamera.so $(DEVICEADAPTERPATH)/$(MMPREFIX)DemoStreamingCamera$(MMSUFFIX)

EXTRA_DIST = DemoStreamingCamera.vcproj license.txt
