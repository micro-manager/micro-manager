pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_PIGCS2.la
libmmgr_dal_PIGCS2_la_SOURCES = Controller.cpp Controller.h \
    PIGCSControllerCom.cpp PIGCSControllerCom.h \
	 PIGCSControllerDLL.cpp PIGCSControllerDLL.h \
	 PIXYStage_DLL.cpp PIXYStage_DLL.h \
	 PIZStage_DLL.cpp PIZStage_DLL.h \
	 PI_GCS_2.cpp PI_GCS_2.h 
libmmgr_dal_PIGCS2_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_PIGCS2_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)
