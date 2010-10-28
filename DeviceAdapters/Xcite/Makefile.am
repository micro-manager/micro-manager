AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Xcite.la
libmmgr_dal_Xcite_la_SOURCES = Xcite.cpp Xcite.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_Xcite_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Xcite_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Xcite.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Xcite$(MMSUFFIX)

EXTRA_DIST =  Xcite.vcproj  Xcite.sln
