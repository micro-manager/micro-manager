AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_PrecisExcite.la
libmmgr_dal_PrecisExcite_la_SOURCES = PrecisExcite.cpp PrecisExcite.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_PrecisExcite_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_PrecisExcite_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_PrecisExcite.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_PrecisExcite$(MMSUFFIX)

EXTRA_DIST = PrecisExcite.vcproj license.txt

