AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_KDV.la
libmmgr_dal_KDV_la_SOURCES = KDV.cpp KDV.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_KDV_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_KDV_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_KDV.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_KDV$(MMSUFFIX)

EXTRA_DIST = KDV.vcproj license.txt

