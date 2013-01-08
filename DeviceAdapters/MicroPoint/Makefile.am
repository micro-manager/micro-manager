AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_MicroPoint.la
libmmgr_dal_MicroPoint_la_SOURCES = MicroPoint.cpp MicroPoint.h h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_MicroPoint_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_MicroPoint_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_MicroPoint.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_MicroPoint$(MMSUFFIX)

EXTRA_DIST = MicroPoint.vcproj license.txt
