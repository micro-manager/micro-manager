AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ZeissCAN29.la
libmmgr_dal_ZeissCAN29_la_SOURCES = ZeissCAN29.cpp ZeissCAN29.h ZeissHub.cpp ZeissMonitor.cpp \
												ZeissModel.cpp
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_ZeissCAN29_la_LDFLAGS = -module
libmmgr_dal_ZeissCAN29_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 

install:
	cp .libs/libmmgr_dal_ZeissCAN29.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ZeissCAN29$(MMSUFFIX)
