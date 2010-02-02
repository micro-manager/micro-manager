AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Utilities.la
libmmgr_dal_Utilities_la_SOURCES = Utilities.cpp Utilities.h
libmmgr_dal_Utilities_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_Utilities_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_Utilities.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Utilities$(MMSUFFIX)

EXTRA_DIST = DAZStage.vcproj license.txt

