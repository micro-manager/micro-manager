AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_XCite120PCExacte.la
libmmgr_dal_XCite120PCExacte_la_SOURCES = XCite.cpp XCite120PC_Exacte.h XCite120PC.h XCite120PC.cpp XCiteExacte.h XCiteExacte.cpp
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_XCite120PCExacte_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_XCite120PCExacte_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_XCite120PCExacte.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_XCite120PCExacte$(MMSUFFIX)

