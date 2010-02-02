AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ASIFW1000.la
libmmgr_dal_ASIFW1000_la_SOURCES = ASIFW1000.cpp ASIFW1000.h ASIFW1000Hub.cpp ASIFW1000Hub.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_ASIFW1000_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_ASIFW1000_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_ASIFW1000.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ASIFW1000$(MMSUFFIX)

EXTRA_DIST = ASIFW1000.vcproj license.txt
