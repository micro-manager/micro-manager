
AUTOMAKE_OPTIONS = foreign subdir-objects

# BOOST_THREAD_VERSION must be set to 2 to compile with old versions of Boost
# (before 2 became the default).
AM_CPPFLAGS = $(BOOST_CPPFLAGS) -DBOOST_THREAD_VERSION=2 -DBOOST_THREAD_DONT_PROVIDE_CONDITION
AM_LDFLAGS = $(BOOST_LDFLAGS) $(MMCORE_APPLEHOST_LDFLAGS)

noinst_LTLIBRARIES = libMMCore.la

libMMCore_la_LIBADD = $(BOOST_SYSTEM_LIB) $(BOOST_DATE_TIME_LIB) $(BOOST_THREAD_LIB) ../MMDevice/libMMDevice.la

libMMCore_la_SOURCES = \
	../MMDevice/MMDevice.h \
	../MMDevice/MMDeviceConstants.h \
	../MMDevice/ModuleInterface.h \
	AppleHost.h \
	CircularBuffer.cpp \
	CircularBuffer.h \
	ConfigGroup.h \
	Configuration.cpp \
	Configuration.h \
	CoreCallback.cpp \
	CoreCallback.h \
	CoreProperty.cpp \
	CoreProperty.h \
	CoreUtils.h \
	DeviceManager.cpp \
	DeviceManager.h \
	Devices/AutoFocusInstance.cpp \
	Devices/AutoFocusInstance.h \
	Devices/CameraInstance.cpp \
	Devices/CameraInstance.h \
	Devices/DeviceInstance.cpp \
	Devices/DeviceInstance.h \
	Devices/GenericDeviceInstance.h \
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
	FrameBuffer.cpp \
	FrameBuffer.h \
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
	LogManager.cpp \
	LogManager.h \
	Logging/GenericStreamSink.h \
	Logging/GenericEntryFilter.h \
	Logging/GenericLinePacket.h \
	Logging/GenericLogger.h \
	Logging/GenericLoggingCore.h \
	Logging/GenericMetadata.h \
	Logging/GenericPacketArray.h \
	Logging/GenericPacketQueue.h \
	Logging/GenericSink.h \
	Logging/Logger.h \
	Logging/Logging.h \
	Logging/Metadata.cpp \
	Logging/Metadata.h \
	Logging/MetadataFormatter.h \
	MMCore.cpp \
	MMCore.h \
	PluginManager.cpp \
	PluginManager.h

if BUILD_CPP_TESTS
UNITTESTS = unittest
endif

SUBDIRS = . $(UNITTESTS)

EXTRA_DIST = license.txt
