AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_AndorSDK3.la
libmmgr_dal_AndorSDK3_la_SOURCES = \
	AOIProperty.cpp \
	AOIProperty.h \
	AOIStrings.h \
	AndorSDK3.cpp \
	AndorSDK3.h \
	AndorSDK3Strings.h \
	BooleanProperty.cpp \
	BooleanProperty.h \
	BooleanPropertyWithPoiseControl.cpp \
	BooleanPropertyWithPoiseControl.h \
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
	SnapShotControl.cpp \
	SnapShotControl.h \
	atcore++.h \
	atcore.h \
	atunpacker.h \
	atutility.h \
	pevents.cpp \
	pevents.h \
	stdafx.cpp \
	stdafx.h \
	targetver.h \
	triggerremapper.h
libmmgr_dal_AndorSDK3_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(LIBATCORE) $(LIBATUTILITY)
libmmgr_dal_AndorSDK3_la_LIBADD = $(MMDEVAPI_LIBADD)

EXTRA_DIST = AndorSDK3.vcproj license.txt
