AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_nPoint.la
libmmgr_dal_nPoint_la_SOURCES = nPC400.cpp nPC400.h nPC400Ctrl.cpp nPC400Ctrl.h nPC400Channel.cpp nPC400Channel.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_nPoint_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_nPoint_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_nPoint.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_nPoint$(MMSUFFIX)

EXTRA_DIST =  nPC400.vcproj  nPC400.sln
