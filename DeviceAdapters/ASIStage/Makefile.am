AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = ASIStage.la
ASIStage_la_SOURCES = ASIStage.cpp ASIStage.h h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
ASIStage_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
ASIStage_la_LDFLAGS = -module

install:
	cp .libs/ASIStage.so $(DEVICEADAPTERPATH)/$(MMPREFIX)ASIStage$(MMSUFFIX)

EXTRA_DIST = ASIStage.vcproj license.txt
