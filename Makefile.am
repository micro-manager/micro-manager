AUTOMAKE_OPTIONS = foreign
ACLOCAL_AMFLAGS = -I m4

if BUILD_WITH_JAVA
MMJAVACODE = MMCoreJ_wrap NativeGUI mmstudio autofocus plugins scripts classext
endif

if BUILD_APP
APP = MMCore $(MMJAVACODE) Test_Programs
endif

if BUILD_PYTHON
PYTHONDIR = MMCorePy_wrap
endif

if BUILD_SECRETDEVICEADAPTERS
SECRETDEVICEADAPTERS = SecretDeviceAdapters
endif

EXTRA_DIST = mmbuild.bat lib/empty.txt build_instructions_win.html build_instructions_unix.txt

SUBDIRS =  MMDevice $(APP) DeviceAdapters $(SECRETDEVICEADAPTERS) $(PYTHONDIR) 


doxygen:
	$(srcdir)/docygen/out/MMDevice
	test -d $(srcdir)/doxygen/out/MMDevice || mkdir $(srcdir)/doxygen/out/MMDevice
	rm -rf $(srcdir)/doxygen/out/MMDevice/*
	doxygen doxygen/MMDevice
	test -d $(srcdir)/doxygen/out/MMCore || mkdir $(srcdir)/doxygen/out/MMCore
	rm -rf $(srcdir)/doxygen/out/MMCore/*
	doxygen doxygen/MMCore
