AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_AndorSDK3.la
libmmgr_dal_AndorSDK3_la_SOURCES = \
	AndorSDK3.cpp \
	AndorSDK3.h \
	AndorSDK3Strings.h \
	AOIProperty.cpp \
	AOIProperty.h \
	AOIStrings.h \
	atcore.h \
	atcore++.h \
	atutility.h \
	BooleanProperty.cpp \
	BooleanProperty.h \
	CallBackManager.cpp \
	CallBackManager.h \
	EnumProperty.cpp \
	EnumProperty.h \
	EventsContainer.h \
	EventsManager.cpp \
	EventsManager.h \
	EventsManagerHelper.h \
	ExposureProperty.cpp \
	ExposureProperty.h \
	FloatProperty.cpp \
	FloatProperty.h \
	IntegerProperty.cpp \
	IntegerProperty.h \
	pevents.cpp \
	pevents.h \
	SnapShotControl.cpp \
	SnapShotControl.h \
	triggerremapper.h

libmmgr_dal_AndorSDK3_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(LIBATCORE) $(LIBATUTILITY)
libmmgr_dal_AndorSDK3_la_LIBADD = $(MMDEVAPI_LIBADD)

EXTRA_DIST = AndorSDK3.vcproj license.txt
