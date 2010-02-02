AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Ludl.la
libmmgr_dal_Ludl_la_SOURCES = Ludl.cpp Ludl.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Ludl_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Ludl_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Ludl.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Ludl$(MMSUFFIX)

EXTRA_DIST = license.txt
