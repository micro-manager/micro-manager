// COPYRIGHT:     (c) 2009-2015 Regents of the University of California
//                (c) 2016 Open Imaging, Inc.
// LICENSE:       This file is distributed under the BSD license.
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
// AUTHOR:        Mark Tsuchida, 2016
//                Based on older code by Arthur Edelstein, 2009

#pragma once

#include "Windows.h"

#include <string>


// RAII wrapper for WNDCLASS, needed to create a window using Win32 API
class SLMWindowClass
{
   HCURSOR hInvisibleCursor_;

   std::string className_;
   WNDCLASS windowClass_;

public:
   static std::string GetClassPrefix() { return "MMiPadSLM_"; }

   SLMWindowClass();
   ~SLMWindowClass();

   // Return the unique class name associated with this window class
   std::string GetClassName() { return className_; }
};