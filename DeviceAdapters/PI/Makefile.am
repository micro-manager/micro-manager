AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = PI.la
PI_la_SOURCES = PI.cpp PI.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
PI_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
PI_la_LDFLAGS = -module

install:
	cp .libs/PI.so $(DEVICEADAPTERPATH)/$(MMPREFIX)PI$(MMSUFFIX)
