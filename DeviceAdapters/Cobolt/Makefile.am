AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Cobolt.la
libmmgr_dal_Cobolt_la_SOURCES = Cobolt.cpp Cobolt.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Cobolt_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Cobolt_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Cobolt.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Cobolt$(MMSUFFIX)
