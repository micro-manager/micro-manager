AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_SimpleAutofocus.la
libmmgr_dal_SimpleAutofocus_la_SOURCES = SimpleAutofocus.cpp SimpleAutofocus.h
libmmgr_dal_SimpleAutofocus_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_SimpleAutofocus_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_SimpleAutofocus.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_SimpleAutofocus$(MMSUFFIX)
