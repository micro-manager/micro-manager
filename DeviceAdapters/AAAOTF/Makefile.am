AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_AAAOTF.la
libmmgr_dal_AAAOTF_la_SOURCES = AAAOTF.cpp AAAOTF.h
libmmgr_dal_AAAOTF_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_AAAOTF_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_AAAOTF.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_AAAOTF$(MMSUFFIX)

EXTRA_DIST = AAAOTF.vcproj license.txt
