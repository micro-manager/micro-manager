// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Loadable module
//
// COPYRIGHT:     University of California, San Francisco, 2013,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Mark Tsuchida

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
