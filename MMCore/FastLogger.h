///////////////////////////////////////////////////////////////////////////////
// FILE:          FastLogger.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Legacy logger interface
// COPYRIGHT:     University of California, San Francisco, 2009-2014
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
// AUTHOR:        Karl Hoover, karl.hoover@ucsf.edu, 20091111
//                Mark Tsuchida, 2013-14.

#pragma once

#include "LogManager.h"

#include <boost/utility.hpp>

#include <string>


class FastLogger : boost::noncopyable
{
   mm::LogManager& manager_;

public:
   FastLogger(mm::LogManager& manager) :
      manager_(manager)
   {}

   // read the current log into memory ( for automated trouble report )
   // since the log file can be extremely large, pass back exactly the buffer that was read
   // CALLER IS RESPONSIBLE FOR delete[] of the array!!
   void LogContents(char** ppContents, unsigned long& len);
   std::string LogPath();
};
