AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Aquinas.la
libmmgr_dal_Aquinas_la_SOURCES = Aquinas.cpp Aquinas.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
libmmgr_dal_Aquinas_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Aquinas_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Aquinas.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Aquinas$(MMSUFFIX)
