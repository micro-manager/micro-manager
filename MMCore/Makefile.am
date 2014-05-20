
AUTOMAKE_OPTIONS = foreign subdir-objects

AM_CPPFLAGS = $(BOOST_CPPFLAGS) $(ZLIB_CPPFLAGS)
AM_CXXFLAGS = $(ZLIB_CFLAGS)
AM_LDFLAGS = $(BOOST_LDFLAGS) $(ZLIB_LDFLAGS) $(MMCORE_APPLEHOST_LDFLAGS)

noinst_LTLIBRARIES = libMMCore.la

# libMMCore currently requires libMMDevice, due to use of CDeviceUtils,
# ImgBuffer, and FrameBuffer. This dependency should probably be removed.
libMMCore_la_LIBADD = $(BOOST_SYSTEM_LIB) $(BOOST_DATE_TIME_LIB) $(ZLIB_LIBS) ../MMDevice/libMMDevice.la

libMMCore_la_SOURCES = \
	../MMDevice/MMDevice.h \
	../MMDevice/MMDeviceConstants.h \
	../MMDevice/ModuleInterface.h \
	AppleHost.h \
	CircularBuffer.cpp \
	CircularBuffer.h \
	Compressor.h \
	ConfigGroup.h \
	Configuration.cpp \
	Configuration.h \
	CoreCallback.cpp \
	CoreCallback.h \
	CoreProperty.cpp \
	CoreProperty.h \
	CoreUtils.h \
	Devices/AutoFocusInstance.cpp \
	Devices/AutoFocusInstance.h \
	Devices/CameraInstance.cpp \
	Devices/CameraInstance.h \
	Devices/DeviceInstance.cpp \
	Devices/DeviceInstance.h \
	Devices/DeviceInstanceBase.h \
	Devices/DeviceInstances.h \
	Devices/GalvoInstance.cpp \
	Devices/GalvoInstance.h \
	Devices/GenericInstance.h \
	Devices/HubInstance.cpp \
	Devices/HubInstance.h \
	Devices/ImageProcessorInstance.cpp \
	Devices/ImageProcessorInstance.h \
	Devices/MagnifierInstance.cpp \
	Devices/MagnifierInstance.h \
	Devices/SLMInstance.cpp \
	Devices/SLMInstance.h \
	Devices/SerialInstance.cpp \
	Devices/SerialInstance.h \
	Devices/ShutterInstance.cpp \
	Devices/ShutterInstance.h \
	Devices/SignalIOInstance.cpp \
	Devices/SignalIOInstance.h \
	Devices/StageInstance.cpp \
	Devices/StageInstance.h \
	Devices/StateInstance.cpp \
	Devices/StateInstance.h \
	Devices/XYStageInstance.cpp \
	Devices/XYStageInstance.h \
	Error.cpp \
	Error.h \
	ErrorCodes.h \
	FastLogger.cpp \
	FastLogger.h \
	Host.cpp \
	Host.h \
	LibraryInfo/LibraryPaths.h \
	LibraryInfo/LibraryPathsUnix.cpp \
	LoadableModules/LoadedDeviceAdapter.cpp \
	LoadableModules/LoadedDeviceAdapter.h \
	LoadableModules/LoadedModule.cpp \
	LoadableModules/LoadedModule.h \
	LoadableModules/LoadedModuleImpl.cpp \
	LoadableModules/LoadedModuleImpl.h \
	LoadableModules/LoadedModuleImplUnix.cpp \
	LoadableModules/LoadedModuleImplUnix.h \
	MMCore.cpp \
	MMCore.h \
	PluginManager.cpp \
	PluginManager.h

SUBDIRS = . unittest

EXTRA_DIST = license.txt
