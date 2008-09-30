AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = K8055.la
K8055_la_SOURCES = K8055.cpp K8055.h K8055Interface.h K8055Interface.cpp
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
K8055_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
K8055_la_LDFLAGS = -module

install:
	cp .libs/K8055.so $(DEVICEADAPTERPATHPATH)/$(MMPREFIX)K8055$(MMSUFFIX)
