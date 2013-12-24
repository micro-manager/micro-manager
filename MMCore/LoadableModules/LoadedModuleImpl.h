#pragma once

#include "LoadedModule.h"

#include <boost/utility.hpp>

class LoadedModuleImpl : boost::noncopyable
{
public:
   static LoadedModuleImpl* NewPlatformImpl(const std::string& filename);

   virtual ~LoadedModuleImpl() {}

   virtual void Unload() = 0;
   virtual void* GetFunction(const char* funcName) = 0;

protected:
   LoadedModuleImpl() {}
};
