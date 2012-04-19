/////////////////////////////////////////////////////////////////////////////
// Project:   UMV001
//!  \file    datatypes.h
//!  \brief    Typedefs for simple data types
//!  \author    ABS GmbH Jena (HBau, RG)
//!  \date    2006-01-06 -> reorganised
//        2010-22-06 ->TC  avoid warning for multiple typedefs (in services_types.h)
/////////////////////////////////////////////////////////////////////////////


#ifndef _DATATYPES_H_
#define _DATATYPES_H_

/////////////////////////////////////////////////////////////////////////////
//! \name Data Types
/////////////////////////////////////////////////////////////////////////////

//!@{

#ifndef _WIN32
  // DSP Firmware
 #ifndef _SERVICES_TYPES_H  //some types already defined in services_types.h
  typedef unsigned long long   u64;
  typedef unsigned long     u32;
  typedef unsigned short    u16;
 #endif
  typedef unsigned char    u08;
  
  typedef signed long long   i64;
  typedef signed long      i32;
  typedef signed short    i16;
  typedef signed char      i08;
  
    typedef float         f32;
    typedef double             f64;

#else
  // Windows
  typedef  unsigned char    u08;
  typedef  unsigned short    u16;
  typedef  unsigned long    u32;
  typedef  unsigned __int64  u64;
  typedef  signed char      i08;
  typedef  signed short    i16;
  typedef  signed long      i32;
    typedef  signed __int64      i64;
    typedef float         f32;
    typedef double             f64;

    typedef  u08*            pu08;
    typedef  u16*                pu16;
    typedef  u32*                pu32;
    typedef  u64*                pu64;
    typedef  i08*                pi08;
    typedef  i16*                pi16;
    typedef  i32*                pi32;
    typedef  i64*                pi64;
    typedef f32*                pf32;               
    typedef f64*                pf64;
    
    #ifdef _WIN64
        typedef void*         pv32;
        typedef void*              pv64;
    #elif  _WIN32
        typedef void*         pv32;
        //typedef PVOID64              pv64;
    #else // non windows
        typedef void*         pv32;
        //typedef unsigned long long*  pv64;        
    #endif

  /*
  #ifndef BOOL
    typedef  i32        BOOL;
    #define  TRUE      1
    #define  FALSE      0
  #endif
  */
#endif // _WIN32

//!@}

#endif // _DATATYPES_H_
