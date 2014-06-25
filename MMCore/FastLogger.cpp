///////////////////////////////////////////////////////////////////////////////
// FILE:          FastLogger.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Legacy logger adapter
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
// AUTHOR:        Karl Hoover, karl.hoover@ucsf.edu, 2009 11 11
//                Mark Tsuchida, 2013-14. Now an adapter for LogManager.

#include "FastLogger.h"

#include "CoreUtils.h"
#include "Error.h"

#include <climits>
#include <fstream>


void FastLogger::LogContents(char** ppContents, unsigned long& len)
{
   *ppContents = 0;
   len = 0;

   std::string filename = manager_.GetPrimaryLogFilename();
   if (filename.empty())
      return;

   manager_.SetPrimaryLogFilename("", false);

   // open to read, and position at the end of the file
   // XXX We simply return NULL if cannot open file or size is too large!
   std::ifstream ifile(filename.c_str(),
         std::ios::in | std::ios::binary | std::ios::ate);
   if (ifile.is_open())
   {
      std::ifstream::pos_type pos = ifile.tellg();

      // XXX This is broken (sort of): on 64-bit Windows, we artificially
      // limit ourselves to 4 GB. But it is probably okay since we don't
      // expect the log contents to be > 4 GB. Fixing would require changing
      // the signature of this function.
      if (pos < ULONG_MAX)
      {
         len = static_cast<unsigned long>(pos);
         *ppContents = new char[len];
         if (0 != *ppContents)
         {
            ifile.seekg(0, std::ios::beg);
            ifile.read(*ppContents, len);
            ifile.close();
         }
      }
   }

   manager_.SetPrimaryLogFilename(filename, false);
}


std::string FastLogger::LogPath()
{
   return manager_.GetPrimaryLogFilename();
}
