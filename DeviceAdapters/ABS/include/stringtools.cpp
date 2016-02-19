///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file     StringTools.cpp
//! 
//! \brief    see header files
//! 
//! \author   ABS GmbH Jena (HBau)
//!           Copyright (C) 2014 - All Rights Reserved
//! 
//! \version  1.1
//! \date     2014/17/09 -> update \n
//! \date     2009/23/06 -> created \n
//! 
///////////////////////////////////////////////////////////////////////////////

#ifndef _CRT_SECURE_NO_WARNINGS
  #define _CRT_SECURE_NO_WARNINGS
#endif

#include "stringtools.h"
#include "SafeUtil.h"

#pragma warning( disable: 4996 )
namespace str
{
  int format_arg_list(std::string& str, const char *fmt, va_list args)
  {
    int   result = -1, length = 255;

    if (!fmt) return result;

    char *buffer = NULL;
    while (result == -1)
    {
      SAFE_DELETE_ARRAY(buffer);
      buffer = new char [length + 1];
      memset(buffer, 0, length + 1);
      result = _vsnprintf(buffer, length, fmt, args);
      length *= 2;
    }
    if (result != -1) str = std::string(buffer);
    SAFE_DELETE_ARRAY(buffer);
    return result;
  }

  int sprintf(std::string &str, const char *fmt, ...)
  {
    int nRC;
    va_list args;
    va_start(args, fmt);
    nRC =  format_arg_list(str, fmt, args);
    va_end(args);
    return nRC;
  }


  int ResizeByZeroTermination(std::string &str)
  {
    const std::string::size_type iPos = str.find('\0');
    if ( iPos != std::string::npos )
      str.erase( iPos, std::string::npos );

    return (int) str.size();
  }

  int ResizeByZeroTermination(std::wstring &str)
  {
    const std::wstring::size_type iPos = str.find(L'\0');
    if ( iPos != std::wstring::npos )
      str.erase( iPos, std::wstring::npos );

    return (int) str.size();
  }

  // replace a substring (strOld) within source / destination string with a new string (strNew)
  // funtion works case sensitive
  int Replace(std::string &str, const std::string strOld, const std::string strNew)
  {
      int iReplacements = 0;       
      using namespace std;
        
      string::size_type iIndex = string::npos;

      do 
      {
          if (iIndex != string::npos)
          {   
              iReplacements++;
              str.replace(iIndex, strOld.size(), strNew);
              iIndex += strNew.size();
          }
          else iIndex = 0;
          iIndex = str.find( strOld, iIndex);

      } while( (iIndex != string::npos) ) ;  

      return iReplacements;
  };


  void ToUpper(std::string &str)
  {
    std::transform(str.begin(), str.end(), str.begin(), toupper );
  }

  void ToLower(std::string &str)
  {
    std::transform(str.begin(), str.end(), str.begin(), tolower );
  }

  void Trim( std::string& strToTrim, const std::string &strTrim )
  {
    // space and tab
    // const std::string strTrim(" \t");
    // Find first non whitespace char in StrToTrim
    std::string::size_type First = strToTrim.find_first_not_of( strTrim );
    // Find last non whitespace char from StrToTrim
    std::string::size_type Last = strToTrim.find_last_not_of( strTrim );

    // Check whether something went wrong?
    if ((First == std::string::npos ) && ( Last == std::string::npos ))
      strToTrim.clear(); // only values to trim => no data

    // Check whether something went wrong?
    if( First == std::string::npos ) First = 0;
    // If something didn't go wrong, Last will be recomputed to get real length of substring
    if( Last != std::string::npos ) Last = ( Last + 1 ) - First;

    // Copy such a string to TrimmedString
    if ((First != 0) || (Last != strToTrim.size())) 
      strToTrim = strToTrim.substr( First, Last );
  };

  // case insensitive compare
  int compareLowCase( std::string strFirst, std::string strSecond )
  {
    ToLower( strFirst );
    ToLower( strSecond );
    return strFirst.compare( strSecond );
  };

}
#pragma  warning( default: 4996 )

