AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_SutterLambda.la
libmmgr_dal_SutterLambda_la_SOURCES = SutterLambda.cpp SutterLambda.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_SutterLambda_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_SutterLambda_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_SutterLambda.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_SutterLambda$(MMSUFFIX)

EXTRA_DIST = SutterLambda.vcproj license.txt
