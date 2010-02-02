AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_K8061.la
libmmgr_dal_K8061_la_SOURCES = K8061.cpp K8061.h K8061Interface.h K8061Interface.cpp \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_K8061_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_K8061_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_K8061.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_K8061$(MMSUFFIX)
