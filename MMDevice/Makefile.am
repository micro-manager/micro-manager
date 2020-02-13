noinst_LTLIBRARIES = libMMDevice.la

noinst_HEADERS = \
	Debayer.h \
	DeviceBase.h \
	DeviceThreads.h \
	DeviceUtils.h \
	FixSnprintf.h \
	ImageMetadata.h \
	ImgBuffer.h \
	MMDevice.h \
	MMDeviceConstants.h \
	ModuleInterface.h \
	Property.h

libMMDevice_la_SOURCES = \
	$(noinst_HEADERS) \
	Debayer.cpp \
	DeviceUtils.cpp \
	ImgBuffer.cpp \
	MMDevice.cpp \
	ModuleInterface.cpp \
	Property.cpp

EXTRA_DIST = license.txt

if INSTALL_MMDEVAPI
libMMDevice_ladir = $(includedir)/$(PACKAGE)/MMDevice
libMMDevice_la_HEADERS = $(noinst_HEADERS)

# Unfortunately automake will not auto-install convenience libraries, so both
# the "install" and "uninstall" procedure must be performed manually.
install: install-am
	test -z "$(DESTDIR)${libdir}" || /bin/mkdir -p "$(DESTDIR)${libdir}"
	cp -a .libs/libMMDevice.a $(DESTDIR)${libdir}/libMMDevice.a

uninstall: uninstall-am
	rm -f $(DESTDIR)${libdir}/libMMDevice.a
endif

SUBDIRS = . unittest
