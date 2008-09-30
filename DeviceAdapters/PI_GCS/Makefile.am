AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = PIGCS.la
PIGCS_la_SOURCES = PI_GCS.cpp PI_GCS.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
PIGCS_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
PIGCS_la_LDFLAGS = -module

install:
	cp .libs/PIGCS.so $(DEVICEADAPTERPATH)/$(MMPREFIX)PIGCS$(MMSUFFIX)
