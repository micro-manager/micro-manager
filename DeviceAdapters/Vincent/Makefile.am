AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Vincent.la
Vincent_la_SOURCES = Vincent.cpp Vincent.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
Vincent_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
Vincent_la_LDFLAGS = -module

install:
	cp .libs/Vincent.so $(IJPATH)/$(MMPREFIX)Vincent

EXTRA_DIST = Vincent_vc8.vcproj Vincent.vcproj license.txt
