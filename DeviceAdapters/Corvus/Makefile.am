AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Corvus.la
libmmgr_dal_Corvus_la_SOURCES = Corvus.cpp Corvus.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Corvus_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Corvus_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Corvus.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Corvus$(MMSUFFIX)

EXTRA_DIST = license.txt
