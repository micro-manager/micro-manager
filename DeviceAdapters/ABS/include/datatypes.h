/////////////////////////////////////////////////////////////////////////////
// Project:   UMV001
//!  \file    datatypes.h
//!  \brief   Typedefs for simple data types
//!  \author  ABS GmbH Jena (HBau, RG)
//!  \date    2006-01-06 -> reorganised
//!  \date    2010-22-06 ->TC  avoid warning for multiple typedefs
//!            (in services_types.h)
//!  \date    2012-09-05 -> updated
//
/////////////////////////////////////////////////////////////////////////////
#ifndef _DATATYPES_H_
#define _DATATYPES_H_

/////////////////////////////////////////////////////////////////////////////
//! \name Data Types
//!@{

#ifdef WIN32                  // Windows

#if defined(_MSC_VER) && (_MSC_VER > 1200)
  #if _MSC_VER > 1200
    typedef unsigned long long    u64;  //!< 64bit unsigned integer value
    typedef signed long long      i64;  //!< 64bit signed integer value
  #else
    typedef unsigned __int64      u64;  //!< 64bit unsigned integer value
    typedef signed __int64        i64;  //!< 64bit signed integer value
  #endif // _MSC_VER > 1200 VC++ 6.0
#else
  typedef unsigned long long      u64;  //!< 64bit unsigned integer value
  typedef signed long long        i64;  //!< 64bit signed integer value
#endif

  typedef unsigned long           u32;  //!< 32bit unsigned integer value
  typedef unsigned short          u16;  //!< 16bit unsigned integer value
  typedef unsigned char           u08;  //!<  8bit unsigned integer value

  typedef signed long             i32;  //!< 32bit signed integer value
  typedef signed short            i16;  //!< 16bit signed integer value
  typedef signed char             i08;  //!<  8bit signed integer value

  typedef float                   f32;  //!< 32bit floating point value (float)
  typedef double                  f64;  //!< 64bit floating point value (double)

#endif // WIN32

#ifdef LINUX   // Linux

  typedef unsigned long long      u64;
  typedef unsigned long           u32;
  typedef unsigned short          u16;
  typedef unsigned char           u08;
  
  typedef signed long long        i64;
  typedef signed long             i32;
  typedef signed short            i16;
  typedef signed char             i08;
  
  typedef float                   f32;
  typedef double                  f64;

#endif // LINUX

#ifdef __VISUALDSPVERSION__   // DSP Firmware

  #if defined(__ADSPBF54x__)
    #include <bfrom.h>
  #endif
  
  #if defined(_LANGUAGE_C)
  
    #ifndef _SERVICES_TYPES_H  //some types already defined in services_types.h
      typedef unsigned long long  u64;
      typedef unsigned long       u32;
      typedef unsigned short      u16;
    #endif
    typedef unsigned char         u08;

    typedef signed long long      i64;
    typedef signed long           i32;
    typedef signed short          i16;
    typedef signed char           i08;

    typedef float                 f32;
    typedef double                f64;
    
  #endif // defined(_LANGUAGE_C)
    
#endif // __VISUALDSPVERSION__

#ifdef __CYU3P_TX__               // Cypress FX3

  typedef uint64_t                u64;
  typedef uint32_t                u32;
  typedef uint16_t                u16;
  typedef uint8_t                 u08;

  typedef int64_t                 i64;
  typedef int32_t                 i32;
  typedef int16_t                 i16;
  typedef int8_t                  i08;

  typedef float                   f32;
  typedef double                  f64;

#endif // __CYU3P_TX__

#if defined(_LANGUAGE_C) || !defined(__VISUALDSPVERSION__)

  // pointer types
  typedef u64*                    pu64;  //!< pointer to 64bit unsigned value
  typedef u32*                    pu32;  //!< pointer to 32bit unsigned value
  typedef u16*                    pu16;  //!< pointer to 16bit unsigned value
  typedef u08*                    pu08;  //!< pointer to  8bit unsigned value

  typedef i64*                    pi64;  //!< pointer to 64bit signed value
  typedef i32*                    pi32;  //!< pointer to 32bit signed value
  typedef i16*                    pi16;  //!< pointer to 16bit signed value
  typedef i08*                    pi08;  //!< pointer to  8bit signed value

  typedef f32*                    pf32;  //!< pointer to 32bit floating point value (float*)
  typedef f64*                    pf64;  //!< pointer to 64bit floating point value (double*)

#endif // #if defined(_LANGUAGE_C) || !defined(__VISUALDSPVERSION__)

//!@}

// structure alignment and packing
#if defined(_MSC_VER) || defined(__BORLANDC__) || defined(__ADSPLPBLACKFIN__)
  // struct packed options unused see #pragma pack(push, 1) at the header files
  #define   STRUCT_PACKED

#elif defined(__GNUC__) 
  #define   STRUCT_PACKED   __attribute__ ((packed)) 
#else
  #error    compiler may be not supported
#endif

#endif // _DATATYPES_H_

