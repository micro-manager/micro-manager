AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Stradus.la
libmmgr_dal_Stradus_la_SOURCES = Stradus.cpp Stradus.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Stradus_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Stradus_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Stradus.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Stradus$(MMSUFFIX)

EXTRA_DIST = license.txt
