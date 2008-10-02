AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = DAShutter.la
DAShutter_la_SOURCES = DAShutter.cpp DAShutter.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
DAShutter_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
DAShutter_la_LDFLAGS = -module

install:
	cp .libs/DAShutter.so $(IJPATH)/$(MMPREFIX)DAShutter

EXTRA_DIST = DAShutter.vcproj license.txt

