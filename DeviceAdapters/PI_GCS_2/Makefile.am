AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_PIGCS2.la
libmmgr_dal_PIGCS2_la_SOURCES = Controller.cpp Controller.h \
    PIGCSControllerCom.cpp PIGCSControllerCom.h \
	 PIGCSControllerDLL.cpp PIGCSControllerDLL.h \
	 PIXYStage_DLL.cpp PIXYStage_DLL.h \
	 PIZStage_DLL.cpp PIZStage_DLL.h \
	 PI_GCS_2.cpp PI_GCS_2.h 
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_PIGCS2_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
libmmgr_dal_PIGCS2_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_PIGCS2.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_PIGCS2$(MMSUFFIX)
