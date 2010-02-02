AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ZeissCAN.la
libmmgr_dal_ZeissCAN_la_SOURCES = ZeissCAN.cpp ZeissCAN.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_ZeissCAN_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_ZeissCAN_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_ZeissCAN.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ZeissCAN$(MMSUFFIX)
