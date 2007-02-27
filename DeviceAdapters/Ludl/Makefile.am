AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Ludl.la
Ludl_la_SOURCES = Ludl.cpp Ludl.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
Ludl_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
Ludl_la_LDFLAGS = -module

install:
	cp .libs/Ludl.so $(IJPATH)/$(MMPREFIX)Ludl

EXTRA_DIST = Ludl_vc8.vcproj Ludl.vcproj license.txt
