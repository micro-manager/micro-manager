AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_AndorLaserCombiner.la
libmmgr_dal_AndorLaserCombiner_la_SOURCES = AndorLaserCombiner.cpp AndorLaserCombiner.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
libmmgr_dal_AndorLaserCombiner_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_AndorLaserCombiner_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_AndorLaserCombiner.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_AndorLaserCombiner$(MMSUFFIX)
