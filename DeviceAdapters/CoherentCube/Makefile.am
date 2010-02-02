AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_CoherentCube.la
libmmgr_dal_CoherentCube_la_SOURCES = CoherentCube.cpp CoherentCube.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
libmmgr_dal_CoherentCube_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_CoherentCube_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_CoherentCube.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_CoherentCube$(MMSUFFIX)
