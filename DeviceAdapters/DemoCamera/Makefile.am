AM_CXXFLAGS = -fpermissive -fweak
lib_LTLIBRARIES = DemoCamera.la
DemoCamera_la_SOURCES = DemoCamera.cpp DemoCamera.h
DemoCamera_la_LDFLAGS = -module 
DemoCamera_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install:
	cp .libs/DemoCamera.so $(IJPATH)/$(MMPREFIX)DemoCamera

EXTRA_DIST = DemoCamera.vcproj license.txt
