AM_CXXFLAGS = -fpermissive -fweak $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_Mightex.la
libmmgr_dal_Mightex_la_SOURCES = Mightex.cpp Mightex.h ../../MMDevice/MMDevice.h
libmmgr_dal_Mightex_la_LDFLAGS = -module 
libmmgr_dal_Mightex_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install: libmmgr_dal_Mightex.la
	cp .libs/libmmgr_dal_Mightex.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Mightex$(MMSUFFIX)

EXTRA_DIST = Mightex.vcproj license.txt
