///////////////////////////////////////////////////////////////////////////////
//! 
//! 
//! \file		StringTools.h
//! 
//! \brief		Tool so implement sprintf for std:strings
//! 
//! \author		ABS GmbH Jena (HBau)
//!				Copyright (C) 2009 - All Rights Reserved
//! 
//! \version	1.0
//! \date		2009/06/23 \n
//! 			 -> created \n
//! 
///////////////////////////////////////////////////////////////////////////////
#ifndef __STRINGTOOLS_H__
#define __STRINGTOOLS_H__

#ifndef _CRT_SECURE_NO_WARNINGS
  #define _CRT_SECURE_NO_WARNINGS  // disable warning C4996
#endif


#include <stdio.h>
#include <stdarg.h>
#include <string>
#include <algorithm>
#include <sstream>
#include <iostream>
#include "SafeUtil.h"
#include "objbase.h"

#if defined(_MSC_VER) && (_MSC_VER >= 1600)  // Visual Studio 2010
  #include <codecvt>
#endif

#pragma warning( disable: 4505 4996)

namespace str
{

  template<typename T1>
  static int resizeByZeroTermination(T1 &str)
  {
    const T1::size_type iPos = str.find( (T1::value_type) 0 );
    if ( iPos != T1::npos )
      str.erase( iPos, T1::npos );

    return (int) str.size();
  }

  static int ResizeByZeroTermination(std::string &str)
  {
    return resizeByZeroTermination<std::string>(str);
  }

  static int ResizeByZeroTermination(std::wstring &str)
  {
    return resizeByZeroTermination<std::wstring>(str);
  }

  static int format_arg_list( std::string & strResult, const char * szFormat, va_list args)
  {
    if ( 0 == szFormat ) 
      return 0;

    strResult.resize( 255, 0 );

    int iResult = _vsnprintf( (char*) strResult.c_str(), strResult.size(), szFormat, args );

    if ( -1 == iResult )
    {
      strResult.resize( 4 * 255, 0 );
      iResult = _vsnprintf( (char*) strResult.c_str(), strResult.size(), szFormat, args );
    }

    if ( -1 != iResult )
      str::ResizeByZeroTermination( strResult );

    return iResult;
  }
  
	static int sprintf(std::string &str, const char *fmt, ...)
	{
		int nRC;
		va_list args;
		va_start(args, fmt);
		nRC =  format_arg_list(str, fmt, args);
		va_end(args);
		return nRC;
	}

  // replace a substring (strOld) within source / destination string with a new string (strNew)
    // function works case sensitive
    static int Replace(std::string & str, const std::string & strOld, const std::string & strNew )
    {
        int iReplacements = 0;
        std::string::size_type iIndex = std::string::npos;

        do 
        {
            if (iIndex != std::string::npos)
            {   
                iReplacements++;
                str.replace( str.begin() + iIndex,
                             str.begin() + iIndex + strOld.size(),
                             strNew );
                iIndex += strNew.size();
            }
            else iIndex = 0;

            iIndex = str.find( strOld, iIndex);

        } while( (iIndex != std::string::npos) ) ;

        return iReplacements;
    };

        
    static void ToUpper(std::string &str)
    {    
        std::transform(str.begin(), str.end(), str.begin(), toupper );
    }

    static void ToLower(std::string &str)
    {    
        std::transform(str.begin(), str.end(), str.begin(), tolower );
    }

    // replace a substring (strOld) within source / destination string with a new string (strNew)
    // function works case sensitive
    static int ReplaceCaseInSensitive(std::string & str, std::string strOld, const std::string & strNew )
    {
      int iReplacements = 0;
      using namespace std;

      string::size_type iIndex = string::npos;
      ToLower(strOld);

      do 
      {
        if (iIndex != string::npos)
        {   
          iReplacements++;
          str.replace( str.begin() + iIndex,
                       str.begin() + iIndex + strOld.size(),
                       strNew );
          iIndex += strNew.size();
        }
        else iIndex = 0;

        string tmp = str;
        ToLower( tmp );
        iIndex = tmp.find( strOld, iIndex);

      } while( (iIndex != string::npos) );

      return iReplacements;
    };

    static void Trim( std::string& strToTrim, const std::string &strTrim = " \t"  )
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


  template<typename T1>
  static T1 hexTo( const std::string & strHexadecimal )
  {
    T1 value;
    std::stringstream ss;
    ss << std::hex << strHexadecimal;
    ss >> value;
    return value;
  }

  template<typename T1>
  static T1 decTo( const std::string & strDecimal )
  {
    T1 value;
    std::stringstream ss;
    ss << std::dec << strDecimal;
    ss >> value;
    return value;
  }

  template<typename T1>
  static T1 floatTo( const std::string & strFloat )
  {
    T1 value;
    std::stringstream ss;
    ss << std::fixed << strFloat;
    ss >> value;
    return value;
  }

  template<typename T1>
  static std::string asHex( const T1 value )
  {
    std::stringstream  ss;
    std::string        strHex;
    ss << value;
    ss >> std::hex >> strHex;
    return strHex;
  }

  template<typename T1>
  static std::string asDec( const T1 value )
  {
    std::stringstream  ss;
    std::string        strDec;
    ss << value;
    ss >> std::dec >> strDec;
    return strDec;
  }

  #if defined(_MSC_VER) && (_MSC_VER >= 1600)  // Visual Studio 2010
  static std::string toString(const std::wstring & strW)
  {
    std::wstring_convert<std::codecvt_utf8_utf16<wchar_t>> converter;
    return converter.to_bytes(strW);
  }
#else
  static std::string toString(const std::wstring & strW )
  {
    std::string str;
    if ( !strW.empty() )   // source string not empty
    {
      u32 dwConversionFlags = 0;
      #if (WINVER >= 0x0601)
        dwConversionFlags = WC_ERR_INVALID_CHARS;
      #endif

      // get size of destination UTF-8 buffer, in CHAR's
      const size_t iCharsNeeded = ::WideCharToMultiByte( CP_UTF8, dwConversionFlags, 
                                                        (LPCWSTR)strW.c_str(), (i32)strW.size(),
                                                         NULL, 0, 
                                                         0, 0);
      if (iCharsNeeded) // no error converting UTF-8 string to UTF-16
      {
        str.resize( iCharsNeeded );
        const size_t iCharsConverted  = ::WideCharToMultiByte(CP_UTF8, dwConversionFlags,             
                                                             (LPCWSTR)strW.c_str(),(i32)strW.size(),
                                                             (LPSTR)str.c_str(), (i32)str.size(), 
                                                             0, 0);
        if (iCharsConverted > 0 ) // no error converting UTF-8 string to UTF-16 
          str.resize(iCharsConverted-1);
        else
          str.clear();
      }
    }
    return str;
  }
#endif  

  static std::wstring toWString(const std::string & str)
  {
    std::wstring strW;
    if ( !str.empty() )   // source string not empty
    {
      // get size of destination UTF-16 buffer, in CHAR's
      const size_t iCharsNeeded = ::MultiByteToWideChar( CP_UTF8, 0, 
                                                        (LPCSTR)str.c_str(), (int)str.size(),
                                                         NULL, 0);
      if (iCharsNeeded) // no error converting UTF-8 string to UTF-16
      {
        strW.resize( iCharsNeeded );
        const size_t iCharsConverted  = ::MultiByteToWideChar(CP_UTF8, 0, 
                                                            (LPCSTR)str.c_str(), (int)str.size(),
                                                            (LPWSTR)strW.c_str(),(int)strW.size());
        if (iCharsConverted > 0 ) // no error converting UTF-8 string to UTF-16 
          strW.resize(iCharsConverted-1);
        else
          strW.clear();
      }
    }
    return strW;
}


  static std::wstring toWString(const GUID & guid)
  {
    std::wstring strGuid;
    strGuid.resize( 40, 0 );
    StringFromGUID2( guid, (LPOLESTR)strGuid.c_str(), (int) strGuid.size() );
    return strGuid;
  }

  static std::string toString(const GUID & guid)
  {
    const std::wstring strGuid = toWString( guid );
    return toString( strGuid );
  }

  static int compareNoCase(const std::wstring & str1, const std::wstring & str2)
  {
    return _wcsnicmp(str1.c_str(), str2.c_str(), max(str1.size(), str2.size()));
  }

  static int compareNoCase(const std::string & str1, const std::string & str2)
  {
    return _strnicmp(str1.c_str(), str2.c_str(), max(str1.size(), str2.size()));
  }
}

#pragma  warning( default: 4996 )

#endif // __STRINGTOOLS_H__