AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Conix.la
libmmgr_dal_Conix_la_SOURCES = Conix.cpp Conix.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Conix_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_Conix_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Conix.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Conix$(MMSUFFIX)
