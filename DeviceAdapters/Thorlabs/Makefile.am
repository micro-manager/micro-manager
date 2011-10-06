AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ThorLabs.la
libmmgr_dal_ThorLabs_la_SOURCES = PiezoZStage.cpp Thorlabs.cpp Thorlabs.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_ThorLabs_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_ThorLabs_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_ThorLabs.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ThorLabs$(MMSUFFIX)

