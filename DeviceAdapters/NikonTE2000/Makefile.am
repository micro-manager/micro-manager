AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = NikonTE2000.la
NikonTE2000_la_SOURCES = NikonTE2000.cpp NikonTE2000.h TEHub.cpp TEHub.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
NikonTE2000_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
NikonTE2000_la_LDFLAGS = -module

install:
	cp .libs/NikonTE2000.so $(IJPATH)/$(MMPREFIX)NikonTE2000

EXTRA_DIST = NikonTE2000.vcproj license.txt
