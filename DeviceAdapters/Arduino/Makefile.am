AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Arduino.la
libmmgr_dal_Arduino_la_SOURCES = Arduino.cpp Arduino.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Arduino_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Arduino_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Arduino.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Arduino$(MMSUFFIX)
