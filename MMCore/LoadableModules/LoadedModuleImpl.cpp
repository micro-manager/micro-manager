#include "LoadedModuleImpl.h"

#ifdef WIN32
#  include "LoadedModuleImplWindows.h"
typedef LoadedModuleImplWindows PlatformLoadedModuleImpl;
#else
#  include "LoadedModuleImplUnix.h"
typedef LoadedModuleImplUnix PlatformLoadedModuleImpl;
#endif


LoadedModuleImpl*
LoadedModuleImpl::NewPlatformImpl(const std::string& filename)
{
   return new PlatformLoadedModuleImpl(filename);
}
