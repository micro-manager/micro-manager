AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = QCam.la
QCam_la_SOURCES = QICamera.cpp QICamera.h
## adding the libtool library gives problems on Intel OS X 10.4, adding the static lib works
QCam_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
QCam_la_LDFLAGS = -module -framework QCam

install:
	cp .libs/QCam.so $(IJPATH)/$(MMPREFIX)QCam

EXTRA_DIST = QCam.vcproj
