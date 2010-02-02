AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ASIWPTR.la
libmmgr_dal_ASIWPTR_la_SOURCES = wptr.cpp wptr.h h
libmmgr_dal_ASIWPTR_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_ASIWPTR_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_ASIWPTR.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_ASIWPTR$(MMSUFFIX)

EXTRA_DIST = ASIWPTR.vcproj license.txt
