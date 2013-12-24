#include "LoadedModuleImplUnix.h"

#include "../Error.h"

#include <dlfcn.h>


static void __attribute__((noreturn))
ThrowDlError()
{
   const char* errorText = dlerror();
   if (!errorText)
      errorText = "Operating system error message not available";
   throw CMMError(errorText);
}


LoadedModuleImplUnix::LoadedModuleImplUnix(const std::string& filename)
{
   int mode = RTLD_NOW | RTLD_LOCAL;

   // Hack to make Andor adapter on Linux work
   // TODO Check if this is still necessary, and if so, why. If it is
   // necessary, add a more generic 'enable-lazy' mechanism.
   if (filename.find("libmmgr_dal_Andor.so") != std::string::npos)
      mode = RTLD_LAZY | RTLD_LOCAL;

   handle_ = dlopen(filename.c_str(), mode);
   if (!handle_)
      ThrowDlError();
}


void
LoadedModuleImplUnix::Unload()
{
   if (!handle_)
      return;

   int err = dlclose(handle_);
   handle_ = 0;
   if (err)
      ThrowDlError();
}


void*
LoadedModuleImplUnix::GetFunction(const char* funcName)
{
   if (!handle_)
      throw CMMError("Cannot get function from unloaded module");

   void* proc = dlsym(handle_, funcName);
   if (!proc)
      ThrowDlError();
   return proc;
}
