AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = MultiShutter.la
MultiShutter_la_SOURCES = MultiShutter.cpp MultiShutter.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
MultiShutter_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
MultiShutter_la_LDFLAGS = -module

install:
	cp .libs/MultiShutter.so $(DEVICEADAPTERPATH)/$(MMPREFIX)MultiShutter$(MMSUFFIX)

EXTRA_DIST = MultiShutter.vcproj license.txt

