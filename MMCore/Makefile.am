AM_CXXFLAGS = $(BOOST_CPPFLAGS)
noinst_LIBRARIES = libMMCore.a
libMMCore_a_SOURCES = MMCore.cpp MMCore.h \
	CircularBuffer.h CircularBuffer.cpp \
	CoreCallback.h CoreCallback.cpp \
	Configuration.h Configuration.cpp \
	ConfigGroup.h \
	CoreProperty.h CoreProperty.cpp \
	CoreUtils.h \
	Error.h Error.cpp \
	ErrorCodes.h\
	PluginManager.h PluginManager.cpp \
	LoadableModules/LoadedModule.h LoadableModules/LoadedModule.cpp \
	LoadableModules/LoadedModuleImpl.h LoadableModules/LoadedModuleImpl.cpp \
	LoadableModules/LoadedModuleImplUnix.h LoadableModules/LoadedModuleImplUnix.cpp \
	LoadableModules/LoadedDeviceAdapter.h LoadableModules/LoadedDeviceAdapter.cpp \
	IMLogger.h \
	FastLogger.h FastLogger.cpp \
        Compressor.h \
	../MMDevice/MMDevice.h ../MMDevice/MMDeviceConstants.h \
	../MMDevice/ModuleInterface.h \
      Host.cpp Host.h \
      AppleHost.h

EXTRA_DIST = license.txt MMCore.vcproj
