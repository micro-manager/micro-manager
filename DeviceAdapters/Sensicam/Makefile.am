AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Sensicam.la
libmmgr_dal_Sensicam_la_SOURCES =  Sensicam.cpp Sensicam.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_Sensicam_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_Sensicam_la_LDFLAGS = -module -lsensicam

install:
	cp .libs/libmmgr_dal_Sensicam.so  $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Sensicam$(MMSUFFIX)
