pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive $(BOOST_CPPFLAGS) $(LIBFREEIMAGE_CXXFLAGS) \
	$(LIBFREEIMAGEPLUS_CXXFLAGS)
pkglib_LTLIBRARIES = libmmgr_dal_GPhoto.la
libmmgr_dal_GPhoto_la_SOURCES = CameraFrontend.cpp CameraFrontend.h 
libmmgr_dal_GPhoto_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_GPhoto_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

# FreeImage library
libmmgr_dal_GPhoto_la_LIBADD += $(LIBFREEIMAGE) $(LIBFREEIMAGEPLUS)

# gphoto2 library
AM_CXXFLAGS += -Igphoto 
libmmgr_dal_GPhoto_la_SOURCES += gphoto/SimpleCam.cpp gphoto/SimpleCam.h
libmmgr_dal_GPhoto_la_LIBADD += $(LIBGPHOTO2) 

install: $(INSTALL_LIBGPHOTO)

install_linux:
	cp .libs/libmmgr_dal_GPhoto$(MMSUFFIX) $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)

install_macos:
	cp .libs/libmmgr_dal_GPhoto$(MMSUFFIX) $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)
	install_name_tool -change $(GPHOTO2_LIBDIR)/libgphoto2.2.dylib @loader_path/libgphoto2/libgphoto2.2.dylib $(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)
	install_name_tool -change $(GPHOTO2_LIBDIR)/libgphoto2_port.0.dylib @loader_path/libgphoto2/libgphoto2_port.0.dylib $(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)
	install_name_tool -change /usr/lib/libltdl.3.dylib /usr/lib/libltdl.dylib $(DEVICEADAPTERPATH)/libmmgr_dal_GPhoto$(MMSUFFIX)
	test -d $(DEVICEADAPTERPATH)/libgphoto2 || mkdir $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2
	test -d $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2 || mkdir $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2
	test -d $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port || mkdir $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port
	rm -rf $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2/*
	rm -rf $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port/*
	cp $(GPHOTO2_LIBDIR)/libgphoto2.2.dylib $(DEVICEADAPTERPATH)/libgphoto2/
	install_name_tool -id @loader_path/libgphoto2/libgphoto2.2.dylib $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2.2.dylib
	install_name_tool -change $(GPHOTO2_LIBDIR)/libgphoto2_port.0.dylib @loader_path/libgphoto2_port.0.dylib $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2.2.dylib
	install_name_tool -change /usr/lib/libltdl.3.dylib /usr/lib/libltdl.dylib $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2.2.dylib
	cp $(GPHOTO2_LIBDIR)/libgphoto2_port.0.dylib $(DEVICEADAPTERPATH)/libgphoto2/
	install_name_tool -id @loader_path/libgphoto2/libgphoto2_port.0.dylib $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port.0.dylib
	install_name_tool -change /usr/lib/libltdl.3.dylib /usr/lib/libltdl.dylib $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port.0.dylib
	cp $(GPHOTO2_LIBDIR)/libgphoto2/$(GPHOTO2_VERSION)/*.so $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2/
	cp $(GPHOTO2_LIBDIR)/libgphoto2_port/$(GPHOTO2_PORT_VERSION)/*.so $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port/
	ls $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2/*.so $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port/*.so | xargs -n 1 install_name_tool -change $(GPHOTO2_LIBDIR)/libgphoto2.2.dylib @loader_path/../libgphoto2.2.dylib
	ls $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2/*.so $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port/*.so | xargs -n 1 install_name_tool -change $(GPHOTO2_LIBDIR)/libgphoto2_port.0.dylib @loader_path/../libgphoto2_port.0.dylib
	ls $(DEVICEADAPTERPATH)/libgphoto2/libgphoto2/*.so $(DESTDIR)$(DEVICEADAPTERPATH)/libgphoto2/libgphoto2_port/*.so | xargs -n 1 install_name_tool -change /usr/lib/libltdl.3.dylib /usr/lib/libltdl.dylib
	osacompile -e 'tell application "PTPCamera" to quit' -o $(DEVICEADAPTERPATH)/libgphoto2/KillPtpCamera.app

EXTRA_DIST = license.txt
