AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_LeicaDMR.la
libmmgr_dal_LeicaDMR_la_SOURCES = LeicaDMR.cpp LeicaDMR.h LeicaDMRHub.cpp LeicaDMRHub.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_LeicaDMR_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_LeicaDMR_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_LeicaDMR.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_LeicaDMR$(MMSUFFIX)

EXTRA_DIST = LeicaDMR.vcproj license.txt
