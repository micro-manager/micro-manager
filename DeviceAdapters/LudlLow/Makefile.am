AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = LudlLow.la
LudlLow_la_SOURCES = Ludl.cpp Ludl.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
LudlLow_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
LudlLow_la_LDFLAGS = -module

install:
	cp .libs/LudlLow.so $(IJPATH)/$(MMPREFIX)LudlLow

##EXTRA_DIST = Ludl_vc8.vcproj Ludl.vcproj license.txt
EXTRA_DIST = license.txt
