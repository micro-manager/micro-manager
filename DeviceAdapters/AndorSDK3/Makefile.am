pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive $(BOOST_CPPFLAGS)
pkglib_LTLIBRARIES = libmmgr_dal_AndorSDK3.la
libmmgr_dal_AndorSDK3_la_SOURCES = AndorSDK3.cpp AOIProperty.cpp BooleanProperty.cpp CallBackManager.cpp EnumProperty.cpp \
                                    EventsManager.cpp FloatProperty.cpp IntegerProperty.cpp pevents.cpp SnapShotControl.cpp \
                                    AndorSDK3.h AndorSDK3Strings.h AOIProperty.h AOIStrings.h atcore.h atcore++.h atutility.h \
                                    BooleanProperty.h CallBackManager.h EnumProperty.h EventsContainer.h EventsManager.h \
                                    EventsManagerHelper.h FloatProperty.h IntegerProperty.h pevents.h SnapShotControl.h triggerremapper.h
libmmgr_dal_AndorSDK3_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(LIBATCORE) $(LIBATUTILITY)
libmmgr_dal_AndorSDK3_la_LIBADD = $(MMDEVAPI_LIBADD)

EXTRA_DIST = AndorSDK3.vcproj license.txt

