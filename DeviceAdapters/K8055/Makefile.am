AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_K8055.la
libmmgr_dal_K8055_la_SOURCES = K8055.cpp K8055.h K8055Interface.h K8055Interface.cpp
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_K8055_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_K8055_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_K8055.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_K8055$(MMSUFFIX)
