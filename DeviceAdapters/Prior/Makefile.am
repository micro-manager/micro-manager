AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Prior.la
Prior_la_SOURCES = Prior.cpp prior.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
Prior_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
Prior_la_LDFLAGS = -module

install:
	cp .libs/Prior.so $(IJPATH)/$(MMPREFIX)Prior

EXTRA_DIST = Prior_vc8.vcproj Prior.vcproj license.txt
