AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = ZeissCAN.la
ZeissCAN_la_SOURCES = ZeissCAN.cpp ZeissCAN.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
ZeissCAN_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
ZeissCAN_la_LDFLAGS = -module

install:
	cp .libs/ZeissCAN.so $(IJPATH)/$(MMPREFIX)ZeissCAN
