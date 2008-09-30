AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Conix.la
Conix_la_SOURCES = Conix.cpp Conix.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
Conix_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
Conix_la_LDFLAGS = -module

install:
	cp .libs/Conix.so $(DEVICEADAPTERPATH)/$(MMPREFIX)Conix$(MMSUFFIX)
