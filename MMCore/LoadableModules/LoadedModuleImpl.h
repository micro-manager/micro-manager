// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Abstract base class for platform-specific loadable module
//                implementation
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
