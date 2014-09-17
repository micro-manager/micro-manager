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


#include <stdio.h>
#include <stdarg.h>
#include <string>
#include <algorithm>
#include <sstream>
#include <iostream>

namespace str
{
  int sprintf(std::string &str, const char *fmt, ...);
  int ResizeByZeroTermination(std::string &str);
  int ResizeByZeroTermination(std::wstring &str);
  // replace a substring (strOld) within source / destination string with a new string (strNew)
  // funtion works case sensitive
  int Replace(std::string &str, const std::string strOld, const std::string strNew);

  void ToUpper(std::string &str);
  void ToLower(std::string &str);
  void Trim( std::string& strToTrim, const std::string &strTrim = " \t"  );

  // case insensitive compare
  int compareLowCase( std::string strFirst, std::string strSecond );

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

#endif // __STRINGTOOLS_H__
