AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Vincent.la
libmmgr_dal_Vincent_la_SOURCES = Vincent.cpp Vincent.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_Vincent_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Vincent_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Vincent.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Vincent$(MMSUFFIX)

EXTRA_DIST =  Vincent.vcproj license.txt
