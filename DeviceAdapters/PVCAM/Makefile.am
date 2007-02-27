AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = PVCAM.la
PVCAM_la_SOURCES = PVCAM.cpp PVCAM.h PVCAMUtils.cpp PVCAMUtils.h PVCAMUniversal.cpp Cascade.cpp
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
PVCAM_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
PVCAM_la_LDFLAGS = -module $(PVCAMFRAMEWORKS)

install:
	cp .libs/PVCAM.so $(IJPATH)/$(MMPREFIX)PVCAM

EXTRA_DIST = PVCAM_vc8.vcproj PVCAM.vcproj license.txt
