AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Andor.la
libmmgr_dal_Andor_la_SOURCES =  Andor.cpp Andor.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Andor_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_Andor_la_LDFLAGS = -module

#EXTRA_DIST = Andor.vcproj license.txt Andor.cpp Andor.h

install:
	cp .libs/libmmgr_dal_Andor.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Andor$(MMSUFFIX)
