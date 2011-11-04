AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_XCiteLed.la
libmmgr_dal_XCiteLed_la_SOURCES = XLed.cpp XLed.h XLedCtrl.cpp XLedCtrl.h XLedDev.cpp XLedDev.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_XCiteLed_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_XCiteLed_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_XCiteLed.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_XCiteLed$(MMSUFFIX)

EXTRA_DIST =  XCiteLed.vcproj  XCiteLed.sln
