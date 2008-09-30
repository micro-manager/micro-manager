AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = ZeissCAN29.la
ZeissCAN29_la_SOURCES = ZeissCAN29.cpp ZeissCAN29.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
ZeissCAN29_la_LDFLAGS = -module
ZeissCAN29_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 

install:
	cp .libs/ZeissCAN29.so $(DEVICEADAPTERPATH)/$(MMPREFIX)ZeissCAN29$(MMSUFFIX)
