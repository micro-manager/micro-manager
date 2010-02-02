AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_PVCAM.la
libmmgr_dal_PVCAM_la_SOURCES = PVCAM.cpp PVCAM.h PVCAMUtils.cpp PVCAMUtils.h PVCAMUniversal.cpp
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_PVCAM_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a $(LIBACE)
libmmgr_dal_PVCAM_la_LDFLAGS = -module $(PVCAMFRAMEWORKS)

install:
	cp .libs/libmmgr_dal_PVCAM.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_PVCAM$(MMSUFFIX)

EXTRA_DIST = PVCAM.vcproj license.txt
