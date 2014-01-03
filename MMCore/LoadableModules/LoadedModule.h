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

#pragma once

#include <boost/utility.hpp>
#include <string>


class LoadedModuleImpl;

class LoadedModule /* final */ : boost::noncopyable
{
public:
   explicit LoadedModule(const std::string& filename);
   ~LoadedModule();

   void Unload(); // For developer use only
   void* GetFunction(const char* funcName);

private:
   LoadedModuleImpl* pImpl_;
   const std::string filename_;
};
