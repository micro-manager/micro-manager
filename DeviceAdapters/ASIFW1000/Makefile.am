AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = ASIFW1000.la
ASIFW1000_la_SOURCES = ASIFW1000.cpp ASIFW1000.h ASIFW1000Hub.cpp ASIFW1000Hub.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
ASIFW1000_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
ASIFW1000_la_LDFLAGS = -module

install:
	cp .libs/ASIFW1000.so $(IJPATH)/$(MMPREFIX)ASIFW1000

EXTRA_DIST = ASIFW1000.vcproj license.txt
