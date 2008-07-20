AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = LeicaDMI.la
LeicaDMI_la_SOURCES = LeicaDMI.cpp LeicaDMI.h LeicaDMIModel.cpp LeicaDMIModel.h LeicaDMIScopeInterface.cpp LeicaDMIScopeInterface.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
LeicaDMI_la_LDFLAGS = -module
LeicaDMI_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 

install:
	cp .libs/LeicaDMI.so $(IJPATH)/$(MMPREFIX)LeicaDMI
