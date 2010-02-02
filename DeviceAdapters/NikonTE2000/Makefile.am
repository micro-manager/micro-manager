AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_NikonTE2000.la
libmmgr_dal_NikonTE2000_la_SOURCES = NikonTE2000.cpp NikonTE2000.h TEHub.cpp TEHub.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_NikonTE2000_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_NikonTE2000_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_NikonTE2000.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_NikonTE2000$(MMSUFFIX)

EXTRA_DIST = NikonTE2000.vcproj NikonTE2000_vc8.sln license.txt
