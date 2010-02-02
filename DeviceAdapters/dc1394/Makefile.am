AM_CXXFLAGS = -fpermissive 
lib_LTLIBRARIES = libmmgr_dal_dc1394.la
libmmgr_dal_dc1394_la_SOURCES = dc1394.cpp dc1394.h 
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
##TODO: find the library in configure
libmmgr_dal_dc1394_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a $(LIBDC1394)
libmmgr_dal_dc1394_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_dc1394.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_dc1394$(MMSUFFIX)

EXTRA_DIST = license.txt
