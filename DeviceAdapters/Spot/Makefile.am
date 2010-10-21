AM_CXXFLAGS = -fpermissive $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_Spot.la
libmmgr_dal_Spot_la_SOURCES = SpotCamera.cpp SpotDevice.cpp CodeUtility.h SpotCamera.h SpotDevice.h ../../MMDevice/DeviceBase.h ../../MMDevice/ImgBuffer.h ../../MMDevice/DeviceUtils.h ../../MMDevice/DeviceThreads.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_Spot_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_Spot_la_LDFLAGS = -module $(SPOTFRAMEWORKS)

install:
	cp .libs/libmmgr_dal_Spot.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_Spot$(MMSUFFIX)

