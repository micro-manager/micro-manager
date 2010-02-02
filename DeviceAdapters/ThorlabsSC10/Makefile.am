AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ThorLabsSC10.la
libmmgr_dal_ThorLabsSC10_la_SOURCES = SC10.cpp SC10.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_ThorLabsSC10_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_ThorLabsSC10_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_ThorLabsSC10.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ThorLabsSC10$(MMSUFFIX)

EXTRA_DIST =  ThorLabsSC10.vcproj license.txt
