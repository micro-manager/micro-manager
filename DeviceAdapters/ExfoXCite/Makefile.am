AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ExfoXCite.la
libmmgr_dal_ExfoXCite_la_SOURCES = ExfoXCite.cpp ExfoXCite.h XCite120PC.h XCite120PC.cpp XCiteExacte.h XCiteExacte.cpp
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_ExfoXCite_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_ExfoXCite_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_ExfoXCite.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ExfoXCite$(MMSUFFIX)

EXTRA_DIST =  ExfoXCite.vcproj  ExfoXCite.sln
