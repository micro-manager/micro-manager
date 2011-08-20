AM_CXXFLAGS = -fpermissive 
lib_LTLIBRARIES = libmmgr_dal_CARVII.la
libmmgr_dal_CARVII_la_SOURCES = CARVII.cpp CARVII.h CARVIIHub.h CARVIIHub.cpp ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h ../../MMDevice/ModuleInterface.h
libmmgr_dal_CARVII_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_CARVII_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_CARVII.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_CARVII$(MMSUFFIX)
