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

#define _CRT_SECURE_NO_WARNINGS

#include <stdio.h>
#include <stdarg.h>
#include <string>
#include <algorithm>
#include <sstream>
#include <iostream>
#include "./SafeUtil.h"

#pragma warning( disable: 4996 )
namespace str
{
	static int format_arg_list(std::string& str, const char *fmt, va_list args)
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

	static int sprintf(std::string &str, const char *fmt, ...)
	{
		int nRC;
		va_list args;
		va_start(args, fmt);
		nRC =  format_arg_list(str, fmt, args);
		va_end(args);
		return nRC;
	}
        
    static int ResizeByZeroTermination(std::string &str)
    {
        std::string::iterator iter;
        iter = str.begin();        
        while (iter != str.end())
        {
            if (*iter == 0)
            {
                str.erase(iter, str.end());
                break;
            }   
            iter++;
        }
        return (int) str.size();
    }

    // replace a substring (strOld) within source / destination string with a new string (strNew)
    // funtion works case sensitive
    static int Replace(std::string &str, const std::string strOld, const std::string strNew)
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

        
    static void ToUpper(std::string &str)
    {    
        std::transform(str.begin(), str.end(), str.begin(), toupper );
    }

    static void ToLower(std::string &str)
    {    
        std::transform(str.begin(), str.end(), str.begin(), tolower );
    }

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

    // case insensitive compare
    static int compareLowCase( std::string strFirst, std::string strSecond )
    {
      ToLower( strFirst );
      ToLower( strSecond );
      return strFirst.compare( strSecond );
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
}
#pragma  warning( default: 4996 )

#endif // __STRINGTOOLS_H__
