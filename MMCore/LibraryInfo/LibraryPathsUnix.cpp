// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//
// COPYRIGHT:     University of California, San Francisco, 2014,
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

// Note: This code must reside in the same binary image as the rest of the
// Core.

#if defined(__APPLE__) || defined(__linux__) // whole file

#include "LibraryPaths.h"


#include "../Error.h"

#include <boost/scoped_array.hpp>

#if defined(__linux__) && !defined(_GNU_SOURCE)
// Provide dladdr()
#   define _GNU_SOURCE
#endif

#include <dlfcn.h>

#ifdef __linux__
#   include <cstring> // for strcpy()
#   include <libgen.h> // for basename()
#   include <unistd.h> // for readlink()
#endif


#ifdef __linux__

static std::string GetExecutablePath()
{
   boost::scoped_array<char> path;
   for (size_t bufsize = 1024; bufsize <= 32768; bufsize *= 2)
   {
      path.reset(new char[bufsize]);
      size_t len = readlink("/proc/self/exe", path.get(), bufsize);
      if (!len)
         throw CMMError("Cannot get path to executable");
      if (len >= bufsize)
         continue;
      return path.get();
   }
}

static std::string GetExecutableName()
{
   const std::string path = GetExecutablePath();
   // basename() can modify the buffer, so make a copy
   boost::scoped_array<char> mutablePath(new char[path.size() + 1]);
   strcpy(mutablePath.get(), path.c_str());
   const char* name = basename(mutablePath.get());
   if (!name)
      throw CMMError("Cannot get executable name");
   return name;
}

#endif // __linux__


namespace MMCorePrivate {

// Note: This can return a relative path on Linux. On OS X, an absolute (though
// not necessarily normalized) path is returned. This should not normally be a
// problem.
std::string GetPathOfThisModule()
{
   // This function is located in this module (obviously), so get info on the
   // dynamic library containing the address of this function.
   Dl_info info;
   int ok = dladdr(reinterpret_cast<void*>(&GetPathOfThisModule), &info);
   if (!ok || !info.dli_fname)
      throw CMMError("Cannot get path to library or executable");

   const std::string path(info.dli_fname);

#ifdef __linux__
   // On Linux, the filename returned by dladdr() may not be a path if we are
   // statically linked into the executable (it appears that the equivalent of
   // argv[0] is returned). In that case, obtain the correct executable path.
   if (path.find('/') == std::string::npos) // not a path
   {
      if (path == GetExecutableName())
         return GetExecutablePath();
   }
#endif // __linux__

   return path;
}

} // namespace MMCorePrivate

#endif // __APPLE__ || __linux__
