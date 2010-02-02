AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_QCam.la
libmmgr_dal_QCam_la_SOURCES = QICamera.cpp QICamera.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
libmmgr_dal_QCam_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_QCam_la_LDFLAGS = -module -framework QCam

install:
	cp .libs/libmmgr_dal_QCam.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_QCam$(MMSUFFIX)

EXTRA_DIST = QCam.vcproj
