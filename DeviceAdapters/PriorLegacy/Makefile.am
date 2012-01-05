AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_PriorLegacy.la
libmmgr_dal_PriorLegacy_la_SOURCES = PriorLegacy.cpp PriorLegacy.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_PriorLegacy_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_PriorLegacy_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_PriorLegacy.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_PriorLegacy$(MMSUFFIX)

EXTRA_DIST = Prior.vcproj license.txt
