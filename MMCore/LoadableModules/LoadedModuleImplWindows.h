#pragma once

#include "LoadedModuleImpl.h"

#include <windows.h>


class LoadedModuleImplWindows: public LoadedModuleImpl
{
public:
   explicit LoadedModuleImplWindows(const std::string& filename);
   virtual void Unload();

   virtual void* GetFunction(const char* funcName);

private:
   HMODULE handle_;
};
