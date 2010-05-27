AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_MaestroServo.la
libmmgr_dal_MaestroServo_la_SOURCES = MaestroServo.cpp MaestroServo.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_MaestroServo_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_MaestroServo_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_MaestroServo.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_MaestroServo$(MMSUFFIX)

EXTRA_DIST =  MaestroServo.vcproj license.txt
