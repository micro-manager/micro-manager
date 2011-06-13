AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_video4linux2.la
libmmgr_dal_video4linux2_la_SOURCES = video4linux2.cpp video4linux2.h
libmmgr_dal_video4linux2_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_video4linux2_la_LDFLAGS = 

install:
	cp .libs/libmmgr_dal_video4linux2.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_video4linux2$(MMSUFFIX)

EXTRA_DIST = 
