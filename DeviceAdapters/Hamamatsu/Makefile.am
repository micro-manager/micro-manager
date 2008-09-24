AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = Hamamatsu.la
Hamamatsu_la_SOURCES = Hamamatsu.cpp Hamamatsu.h ../../MMDevice/DeviceBase.h ../../MMDevice/ImgBuffer.h ../../MMDevice/DeviceUtils.h ../../MMDevice/DeviceTHreads.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
Hamamatsu_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
##Hamamatsu_la_LDFLAGS = -module -framework dcamapi
Hamamatsu_la_LDFLAGS = -module $(DCAMFRAMEWORKS)

install:
	cp .libs/Hamamatsu.so $(IJPATH)/$(MMPREFIX)Hamamatsu

install-strip:
	cp .libs/Hamamatsu.so $(IJPATH)/$(MMPREFIX)Hamamatsu
	strip -x $(IJPATH)/$(MMPREFIX)Hamamatsu

EXTRA_DIST = Hamamatsu.vcproj license.txt

