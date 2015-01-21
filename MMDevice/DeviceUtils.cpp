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

#include "DeviceUtils.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sstream>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#else
   #include <unistd.h>
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
 *
 * This function is not thread-safe, and the return value is only valid until
 * the next call to ConvertToString().
 */
const char* CDeviceUtils::ConvertToString(long lnVal)
{
   snprintf(m_pszBuffer, MM::MaxStrLength-1, "%ld", lnVal); 
   return m_pszBuffer;
}

/**
 * Convert int value to string.
 *
 * This function is not thread-safe, and the return value is only valid until
 * the next call to ConvertToString().
 */
const char* CDeviceUtils::ConvertToString(int intVal)
{
   return ConvertToString((long)intVal);
}

/**
 * Convert double value to string.
 *
 * This function is not thread-safe, and the return value is only valid until
 * the next call to ConvertToString().
 */
const char* CDeviceUtils::ConvertToString(double dVal)
{
   snprintf(m_pszBuffer, MM::MaxStrLength-1, "%.2f", dVal); 
   return m_pszBuffer;
}

/**
 * Convert boolean value to string.
 *
 * This function is not thread-safe, and the return value is only valid until
 * the next call to ConvertToString().
 */
const char* CDeviceUtils::ConvertToString(bool val)
{
   snprintf(m_pszBuffer, MM::MaxStrLength-1, "%s", val ? "1" : "0"); 
   return m_pszBuffer;
}


// from a vectors of chars make a string like "0x00 0x01 0x02....
std::string CDeviceUtils::HexRep(std::vector<unsigned char>  values)
{
   std::ostringstream ret;
   for(std::vector<unsigned char>::iterator i = values.begin(); i != values.end(); ++i)
   {
      if (i!=values.begin())
         ret << " ";
      std::ios_base::fmtflags restore = ret.flags(  std::ios::hex | std::ios::showbase );
      ret << (unsigned int)(*i);
      ret.flags(restore);
   }
   return ret.str();

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

/**
 * Block the current thread for the specified interval in milliseconds.
 */
void CDeviceUtils::SleepMs(long periodMs)
{
#ifdef WIN32
   Sleep(periodMs);
#else
   usleep(periodMs * 1000);
#endif
}

/**
 * Yield to other threads for the specified interval in microseconds.
 */
void CDeviceUtils::NapMicros(unsigned long period)
{
#ifdef WIN32
   Sleep(period/1000);
#else
   usleep(period);
#endif
}


bool CDeviceUtils::CheckEnvironment(std::string env)
{
   bool bvalue = false;
   if( 0 < env.length())
   {
      char *pvalue = ::getenv(env.c_str());
      if( 0 != pvalue)
      {
         if( 0 != *pvalue)
         {
            char initial =  (char)tolower(*pvalue);
            bvalue = ('0' != initial) && ('f' != initial) && ( 'n' != initial);
         }
      }
   }
   return bvalue;
}



#ifdef _WINDOWS
 
int gettimeofday(struct timeval *tv, struct timezone *tz)
{
  FILETIME ft;
  unsigned __int64 tmpres = 0;
  static int tzflag;
 
  if (NULL != tv)
  {
    GetSystemTimeAsFileTime(&ft);
 
    tmpres |= ft.dwHighDateTime;
    tmpres <<= 32;
    tmpres |= ft.dwLowDateTime;
 
    /*converting file time to unix epoch*/
    tmpres -= DELTA_EPOCH_IN_MICROSECS; 
    tmpres /= 10;  /*convert into microseconds*/
    tv->tv_sec = (long)(tmpres / 1000000UL);
    tv->tv_usec = (long)(tmpres % 1000000UL);
  }
 
  if (NULL != tz)
  {
    if (!tzflag)
    {
      _tzset();
      tzflag++;
    }
    tz->tz_minuteswest = _timezone / 60;
    tz->tz_dsttime = _daylight;
  }
 
  return 0;
}

#endif
