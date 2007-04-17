AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = ASI.la
ASI_la_SOURCES = ASI.cpp ASI.h ASIFW1000Hub.cpp ASIFW1000Hub.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
ASI_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
ASI_la_LDFLAGS = -module

install:
	cp .libs/ASI.so $(IJPATH)/$(MMPREFIX)ASI

EXTRA_DIST = ASI.vcproj license.txt
