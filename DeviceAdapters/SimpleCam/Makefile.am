AM_CXXFLAGS = -fpermissive $(BOOST_CPPFLAGS) -I../../../3rdpartypublic/FreeImage/Dist/ -I../../../3rdpartypublic/FreeImage/Wrapper/FreeImagePlus/
lib_LTLIBRARIES = libmmgr_dal_GPhoto.la
libmmgr_dal_GPhoto_la_SOURCES = CameraFrontend.cpp CameraFrontend.h 
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
## libmmgr_dal_GPhoto_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a ../../../3rdpartypublic/FreeImage/Dist/libfreeimage.a
libmmgr_dal_GPhoto_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a -lfreeimage
libmmgr_dal_GPhoto_la_LDFLAGS = -module

# gphoto2 driver: uses libgphoto2 built using macports
AM_CXXFLAGS += -Igphoto 
libmmgr_dal_GPhoto_la_SOURCES += gphoto/SimpleCam.cpp gphoto/SimpleCam.h
libmmgr_dal_GPhoto_la_LIBADD += -lgphoto2
# libmmgr_dal_GPhoto_la_LIBADD += /opt/local/lib/libgphoto2.la /opt/local/lib/libgphoto2_port.la

install:
	cp .libs/libmmgr_dal_GPhoto.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)

EXTRA_DIST = license.txt
