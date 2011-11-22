AM_CXXFLAGS = -fpermissive -fweak $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_VariLC.la
libmmgr_dal_VariLC_la_SOURCES = VariLC.cpp VariLC.h ../../MMDevice/MMDevice.h
libmmgr_dal_VariLC_la_LDFLAGS = -module 
libmmgr_dal_VariLC_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install:
	cp .libs/libmmgr_dal_VariLC.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_VariLC$(MMSUFFIX)
