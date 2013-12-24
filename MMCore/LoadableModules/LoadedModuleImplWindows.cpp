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

#include "LoadedModuleImplWindows.h"

#include "../Error.h"

#include <boost/algorithm/string.hpp>


static void __declspec(noreturn)
ThrowLastError()
{
   std::string errorText;

   DWORD err = GetLastError();
   LPSTR pMsgBuf(0);
   if (FormatMessageA( 
         FORMAT_MESSAGE_ALLOCATE_BUFFER | 
         FORMAT_MESSAGE_FROM_SYSTEM | 
         FORMAT_MESSAGE_IGNORE_INSERTS,
         NULL,
         err,
         MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
         (LPSTR)&pMsgBuf,
         0,
         NULL) && pMsgBuf)
   {
      errorText = pMsgBuf;

      // Windows error messages sometimes have trailing newlines
      boost::algorithm::trim(errorText);

      // This particular message can be rather misleading.
      if (errorText == "The specified module could not be found.") {
         errorText = "The module, or a module it depends upon, could not be found "
            "(Windows error: " + errorText + ")";
      }
   }
   if (pMsgBuf)
   {
      LocalFree(pMsgBuf);
   }

   if (errorText.empty()) {
      errorText = "Operating system error message not available";
   }

   throw CMMError(errorText);
}


LoadedModuleImplWindows::LoadedModuleImplWindows(const std::string& filename)
{
   int saveErrorMode = SetErrorMode(SEM_NOOPENFILEERRORBOX | SEM_FAILCRITICALERRORS);
   handle_ = LoadLibrary(filename.c_str());
   SetErrorMode(saveErrorMode);
   if (!handle_)
      ThrowLastError();
}


void
LoadedModuleImplWindows::Unload()
{
   if (!handle_)
      return;

   BOOL ok = FreeLibrary(handle_);
   handle_ = 0;
   if (!ok)
      ThrowLastError();
}


void*
LoadedModuleImplWindows::GetFunction(const char* funcName)
{
   if (!handle_)
      throw CMMError("Cannot get function from unloaded module");

   void* proc = GetProcAddress(handle_, funcName);
   if (!proc)
      ThrowLastError();
   return proc;
}
