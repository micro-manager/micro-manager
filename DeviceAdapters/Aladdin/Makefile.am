AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Aladdin.la
libmmgr_dal_Aladdin_la_SOURCES = Aladdin.cpp Aladdin.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_Aladdin_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_Aladdin_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Aladdin.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Aladdin$(MMSUFFIX)

