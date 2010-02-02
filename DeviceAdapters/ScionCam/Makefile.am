AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_ScionCam.la
libmmgr_dal_ScionCam_la_SOURCES = ScionCamera.cpp ScionCamera.h capture.cpp utilities.cpp
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_ScionCam_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_ScionCam_la_LDFLAGS = -module -framework sfwlib -framework sfwcore

install:
	cp .libs/libmmgr_dal_ScionCam.so $(DEVICEADAPTERPATH)/libmmgr_dal_ScionCam$(MMSUFFIX)

EXTRA_DIST = ScionCamera.vcproj capture.h device.h iformat.h imageinfo.h interface.h settings.h sfwlib.h thread.h resource.h ScionCamera.rc MMConfig_scion.cfg license.txt
