AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Ludl.la
Ludl_la_SOURCES = Ludl.cpp Ludl.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
Ludl_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
Ludl_la_LDFLAGS = -module

install:
	cp .libs/Ludl.so $(DEVICEADAPTERPATH)/$(MMPREFIX)$(thislib)$(MMSUFFIX)

install-strip:
	cp .libs/$(MMPREFIX)Ludl.so $(DEVICEADAPTERPATH)/Ludl$(MMSUFFIX)
	strip -x (DEVICEADAPTERPATH)/$(MMPREFIX)Ludl$(MMSUFFIX)

##EXTRA_DIST = Ludl_vc8.vcproj Ludl.vcproj license.txt
EXTRA_DIST = license.txt
