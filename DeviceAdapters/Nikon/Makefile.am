AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Nikon.la
libmmgr_dal_Nikon_la_SOURCES = Nikon.cpp Nikon.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_Nikon_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Nikon_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Nikon.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Nikon$(MMSUFFIX)

EXTRA_DIST = Nikon.vcproj license.txt

