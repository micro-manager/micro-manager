AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_Hamamatsu.la
libmmgr_dal_Hamamatsu_la_SOURCES = Hamamatsu.cpp Hamamatsu.h ../../MMDevice/DeviceBase.h ../../MMDevice/ImgBuffer.h ../../MMDevice/DeviceUtils.h ../../MMDevice/DeviceTHreads.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_Hamamatsu_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
##Hamamatsu_la_LDFLAGS = -module -framework dcamapi
libmmgr_dal_Hamamatsu_la_LDFLAGS = -module $(DCAMFRAMEWORKS)

install:
	cp .libs/libmmgr_dal_Hamamatsu.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Hamamatsu$(MMSUFFIX)

EXTRA_DIST = Hamamatsu.vcproj license.txt

