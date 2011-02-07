AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Sapphire.la
libmmgr_dal_Sapphire_la_SOURCES = Sapphire.cpp Sapphire.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
libmmgr_dal_Sapphire_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Sapphire_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Sapphire.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Sapphire$(MMSUFFIX)
