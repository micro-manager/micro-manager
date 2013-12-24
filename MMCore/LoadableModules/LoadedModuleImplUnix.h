#include "LoadedModuleImpl.h"


class LoadedModuleImplUnix : public LoadedModuleImpl
{
public:
   explicit LoadedModuleImplUnix(const std::string& filename);
   virtual void Unload();

   virtual void* GetFunction(const char* funcName);

private:
   void* handle_;
};
