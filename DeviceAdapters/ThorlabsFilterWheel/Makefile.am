AM_CXXFLAGS = -fpermissive -fweak
lib_LTLIBRARIES = libmmgr_dal_ThorlabsFilterWheel.la
libmmgr_dal_ThorlabsFilterWheel_la_SOURCES = FilterWheel.cpp FilterWheel.h
libmmgr_dal_ThorlabsFilterWheel_la_LDFLAGS = -module 
libmmgr_dal_ThorlabsFilterWheel_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a

install:
	cp .libs/libmmgr_dal_ThorlabsFilterWheel.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ThorlabsFilterWheel$(MMSUFFIX)

EXTRA_DIST = ThorlabsFilterWheel.vcproj license.txt
