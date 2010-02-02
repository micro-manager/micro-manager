AM_CXXFLAGS = -fpermissive -fweak
lib_LTLIBRARIES = libmmgr_dal_ThorlabsDCxxxx.la
libmmgr_dal_ThorlabsDCxxxx_la_SOURCES = DCxxxx_Plugin.cpp DCxxxx_Plugin.h
libmmgr_dal_ThorlabsDCxxxx_la_LDFLAGS = -module 
libmmgr_dal_ThorlabsDCxxxx_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install:
	cp .libs/libmmgr_dal_ThorlabsDCxxxx.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ThorlabsDCxxxx$(MMSUFFIX)
