AM_CXXFLAGS = -fpermissive -fweak $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_DemoCamera.la
libmmgr_dal_DemoCamera_la_SOURCES = DemoCamera.cpp DemoCamera.h ../../MMDevice/MMDevice.h
libmmgr_dal_DemoCamera_la_LDFLAGS = -module 
libmmgr_dal_DemoCamera_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install: libmmgr_dal_DemoCamera.la
	cp .libs/libmmgr_dal_DemoCamera.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_DemoCamera$(MMSUFFIX)

EXTRA_DIST = DemoCamera.vcproj license.txt
