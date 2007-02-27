AUTOMAKE_OPTIONS = foreign

SUBDIRS =  MMCore MMDevice MMCoreJ_wrap mmstudio ModuleTest Test_MMCore Test_Serial DeviceAdapters bin

EXTRA_DIST = bin mmbuild.bat lib/empty.txt build_instructions_win.html build_instructions_unix.txt

dist-hook:
	rm -rf `find $(distdir)/bin -name CVS`
