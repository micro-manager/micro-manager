AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = K8061.la
K8061_la_SOURCES = K8061.cpp K8061.h K8061Interface.h K8061Interface.cpp \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
K8061_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
K8061_la_LDFLAGS = -module

install:
	cp .libs/K8061.so $(DEVICEADAPTERPATH)/$(MMPREFIX)K8061$(MMSUFFIX)
