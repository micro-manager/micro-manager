AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Scientifica.la
libmmgr_dal_Scientifica_la_SOURCES = Scientifica.cpp Scientifica.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Scientifica_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Scientifica_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Scientifica.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Scientifica$(MMSUFFIX)

EXTRA_DIST = license.txt
