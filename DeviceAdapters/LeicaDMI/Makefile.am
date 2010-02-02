AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_LeicaDMI.la
libmmgr_dal_LeicaDMI_la_SOURCES = LeicaDMI.cpp LeicaDMI.h LeicaDMIModel.cpp LeicaDMIModel.h LeicaDMIScopeInterface.cpp LeicaDMIScopeInterface.h
libmmgr_dal_LeicaDMI_la_LDFLAGS = -module
libmmgr_dal_LeicaDMI_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 

install:
	cp .libs/libmmgr_dal_LeicaDMI.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_LeicaDMI$(MMSUFFIX)
