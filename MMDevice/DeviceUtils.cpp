///////////////////////////////////////////////////////////////////////////////
// FILE:          DeviceUtils.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Class with utility methods for building device adapters
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com 06/08/2005
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//

#include "DeviceUtils.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#pragma warning(disable : 4996)

#ifdef WIN32
   #define snprintf _snprintf 
#endif

char CDeviceUtils::m_pszBuffer[MM::MaxStrLength]={""};

/**
 * Copies strings with predefined size limit.
 */
bool CDeviceUtils::CopyLimitedString(char* target, const char* source)
{
   strncpy(target, source, MM::MaxStrLength - 1);
   if ((MM::MaxStrLength - 1) < strlen(source))
   {
      target[MM::MaxStrLength - 1] = 0;
      return false; // string truncated
   }
   else
      return true;
}

/**
 * Programmatic access to the system-wide string size limit.
 */
unsigned CDeviceUtils::GetMaxStringLength()
{
   return MM::MaxStrLength;
}

/**
 * Convert long value to string.
 */
const char* CDeviceUtils::ConvertToString(long lnVal)
{
   // return ltoa(lnVal, m_pszBuffer, 10);
   snprintf(m_pszBuffer, MM::MaxStrLength-1, "%ld", lnVal); 
   return m_pszBuffer;
}

/**
 * Convert double value to string.
 */
const char* CDeviceUtils::ConvertToString(double dVal)
{
   //return _gcvt(dVal, 12, m_pszBuffer);
   snprintf(m_pszBuffer, MM::MaxStrLength-1, "%.2f", dVal); 
   return m_pszBuffer;
}

/**
 * Parse the string and return an array of tokens.
 * @param str - input string
 * @param tokens - output array of tokens
 * @param delimiters - a string containing a set of single-character token delimiters
 */
void CDeviceUtils::Tokenize(const std::string& str, std::vector<std::string>& tokens, const std::string& delimiters)
{
    // Skip delimiters at beginning.
   std::string::size_type lastPos = str.find_first_not_of(delimiters, 0);
    // Find first "non-delimiter".
   std::string::size_type pos     = str.find_first_of(delimiters, lastPos);

   while (std::string::npos != pos || std::string::npos != lastPos)
    {
        // Found a token, add it to the vector.
        tokens.push_back(str.substr(lastPos, pos - lastPos));
        // Skip delimiters.  Note the "not_of"
        lastPos = str.find_first_not_of(delimiters, pos);
        // Find next "non-delimiter"
        pos = str.find_first_of(delimiters, lastPos);
    }
}

