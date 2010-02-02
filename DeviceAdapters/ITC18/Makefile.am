AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_MMITC18.la
libmmgr_dal_MMITC18_la_SOURCES = MMITC18.cpp MMITC18.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_MMITC18_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_MMITC18_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_MMITC18.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_MMITC18$(MMSUFFIX)
