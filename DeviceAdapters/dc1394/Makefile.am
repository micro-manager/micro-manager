AM_CXXFLAGS = -fpermissive 
lib_LTLIBRARIES = dc1394.la
dc1394_la_SOURCES = dc1394.cpp dc1394.h 
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
##TODO: find the library in configure
dc1394_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a /usr/local/lib/libdc1394.a
dc1394_la_LDFLAGS = -module

install:
	cp .libs/dc1394.so $(DEVICEADAPTERPATH)/$(MMPREFIX)dc1394$(MMSUFFIX)

EXTRA_DIST = license.txt
