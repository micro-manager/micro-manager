AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = SutterLambda.la
SutterLambda_la_SOURCES = SutterLambda.cpp SutterLambda.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
SutterLambda_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
SutterLambda_la_LDFLAGS = -module

install:
	cp .libs/SutterLambda.so $(DEVICEADAPTERPATH)/$(MMPREFIX)SutterLambda$(MMSUFFIX)

EXTRA_DIST = SutterLambda.vcproj license.txt
