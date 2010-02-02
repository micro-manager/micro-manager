AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_PIGCS.la
libmmgr_dal_PIGCS_la_SOURCES = PI_GCS.cpp PI_GCS.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_PIGCS_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_PIGCS_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_PIGCS.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_PIGCS$(MMSUFFIX)
