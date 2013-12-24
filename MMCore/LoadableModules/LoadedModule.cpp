#include "LoadedModule.h"

#include "LoadedModuleImpl.h"
#include "../CoreUtils.h"
#include "../Error.h"


LoadedModule::LoadedModule(const std::string& filename) :
   filename_(filename)
{
   try
   {
      pImpl_ = LoadedModuleImpl::NewPlatformImpl(filename_);
   }
   catch (const CMMError& e)
   {
      throw CMMError("Failed to load module " + ToQuotedString(filename_), e);
   }
}


LoadedModule::~LoadedModule()
{
   delete pImpl_;
}


void
LoadedModule::Unload()
{
   try
   {
      pImpl_->Unload();
   }
   catch (const CMMError& e)
   {
      throw CMMError("Cannot unload module " + ToQuotedString(filename_), e);
   }
}


void*
LoadedModule::GetFunction(const char* funcName)
{
   try
   {
      return pImpl_->GetFunction(funcName);
   }
   catch (const CMMError& e)
   {
      throw CMMError("Cannot find function " + ToString(funcName) + "() in module " +
            ToQuotedString(filename_), e);
   }
}
