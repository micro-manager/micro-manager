AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Pecon.la
Pecon_la_SOURCES = Pecon.cpp Pecon.h h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
Pecon_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
Pecon_la_LDFLAGS = -module

install:
	cp .libs/Pecon.so $(DEVICEADAPTERPATH)/$(MMPREFIX)Pecon$(MMSUFFIX)

EXTRA_DIST = Pecon.vcproj license.txt
