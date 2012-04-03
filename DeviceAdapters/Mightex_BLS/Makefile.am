AM_CXXFLAGS = -fpermissive -fweak $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_MightexBLS.la
libmmgr_dal_MightexBLS_la_SOURCES = Mightex.cpp Mightex.h ../../MMDevice/MMDevice.h
libmmgr_dal_MightexBLS_la_LDFLAGS = -module 
libmmgr_dal_MightexBLS_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install: libmmgr_dal_MightexBLS.la
	cp .libs/libmmgr_dal_MightexBLS.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_MightexBLS$(MMSUFFIX)
