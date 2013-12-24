// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// DESCRIPTION:   Loadable module implementation for Windows.
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
// AUTHOR:        Mark Tsuchida,
//                based on parts of CPluginManager by Nenad Amodaj

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
