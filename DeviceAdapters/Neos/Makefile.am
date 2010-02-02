AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Neos.la
libmmgr_dal_Neos_la_SOURCES = Neos.cpp Neos.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_Neos_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Neos_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Neos.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Neos$(MMSUFFIX)

EXTRA_DIST =  Neos.vcproj license.txt
