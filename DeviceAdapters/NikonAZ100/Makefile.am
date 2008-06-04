AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = NikonAZ100.la
NikonAZ100_la_SOURCES = NikonAZ100.cpp NikonAZ100.h AZHub.h AZHub.cpp
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
NikonAZ100_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
NikonAZ100_la_LDFLAGS = -module

install:
	cp .libs/NikonAZ100.so $(IJPATH)/$(MMPREFIX)NikonAZ100

EXTRA_DIST = NikonAZ100.vcproj NikonAZ100_vc8.sln license.txt
