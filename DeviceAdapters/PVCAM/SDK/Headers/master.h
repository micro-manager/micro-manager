/*****************************************************************************/
/*        Copyright (C) Roper Scientific, Inc. 2007 All rights reserved.     */
/*****************************************************************************/
#ifndef _MASTER_H
#define _MASTER_H

#ifndef WIN32
#error OS Not Supported
#endif

/******************************** ANSI Types *********************************/
#if defined  __cplusplus

// BORLAND   C++

#define PV_C_PLUS_PLUS

#elif defined  __cplusplus__

// MICROSOFT C++   This allows us to insert the proper compiler flags,
//                 in PVCAM.H for example, to cope properly with C++ definitions.

#define PV_C_PLUS_PLUS

#endif


#if defined  _MSC_VER

// MICROSOFT C++   VS 2005 wants to use safe string
// functions - we have to turn this off for now.

#pragma message("Disabling String Safe Warnings")
#pragma warning(disable : 4996)

#endif

/**************************** Calling Conventions ****************************/

#define PV_CDECL __cdecl

#if 0  // for 64 bit...

#if defined CDECL_CALL_CONV                      /* Use the '_cdecl' calling convention */
#define PV_DECL __declspec(dllexport) PV_CDECL /*  or '__stdcall' calling convention  */
#define PV_CALL_CONV PV_CDECL
#else                                            /*  as appropriate.                    */
#define PV_DECL __declspec(dllexport) __stdcall
#define PV_CALL_CONV __stdcall
#endif

#define LIB_EXPORT __declspec(dllexport)

#else

#if defined CDECL_CALL_CONV                     // Use the '_cdecl' calling convention 
#define PV_DECL PV_CDECL					//  or '__stdcall' calling convention 
#define PV_CALL_CONV PV_CDECL
#else                                           //  as appropriate.                    
#define PV_DECL __stdcall
#define PV_CALL_CONV __stdcall
#endif

#define LIB_EXPORT

#endif

/**************************** PVCAM Pointer Types ****************************/
#define PV_PTR_DECL  *
#define PV_BUFP_DECL *

/******************************** PVCAM Types ********************************/
enum { PV_FAIL, PV_OK };

typedef unsigned short rs_bool, PV_PTR_DECL  rs_bool_ptr;
typedef char                    PV_PTR_DECL  char_ptr;
typedef signed char    int8,    PV_PTR_DECL  int8_ptr;
typedef unsigned char  uns8,    PV_PTR_DECL  uns8_ptr;
typedef short          int16,   PV_PTR_DECL  int16_ptr;
typedef unsigned short uns16,   PV_PTR_DECL  uns16_ptr;
typedef long           int32,   PV_PTR_DECL  int32_ptr;
typedef unsigned long  uns32,   PV_PTR_DECL  uns32_ptr;
typedef double         flt64,   PV_PTR_DECL  flt64_ptr;
typedef void                    PV_BUFP_DECL void_ptr;
typedef void_ptr                PV_BUFP_DECL void_ptr_ptr;

#if defined(_MSC_VER)
typedef unsigned __int64 ulong64, PV_PTR_DECL ulong64_ptr;
typedef signed __int64 long64, PV_PTR_DECL long64_ptr;
#else
typedef unsigned long long ulong64, PV_PTR_DECL ulong64_ptr;;
typedef signed long long long64, PV_PTR_DECL long64_ptr;
#endif

typedef const rs_bool PV_PTR_DECL rs_bool_const_ptr;
typedef const char    PV_PTR_DECL char_const_ptr;
typedef const int8    PV_PTR_DECL int8_const_ptr;
typedef const uns8    PV_PTR_DECL uns8_const_ptr;
typedef const int16   PV_PTR_DECL int16_const_ptr;
typedef const uns16   PV_PTR_DECL uns16_const_ptr;
typedef const int32   PV_PTR_DECL int32_const_ptr;
typedef const uns32   PV_PTR_DECL uns32_const_ptr;
typedef const flt64   PV_PTR_DECL flt64_const_ptr;

/****************************** PVCAM Constants ******************************/
#ifndef FALSE
#define FALSE  PV_FAIL      /* FALSE == 0                                  */
#endif

#ifndef TRUE
#define TRUE   PV_OK        /* TRUE  == 1                                  */
#endif

#define BIG_ENDIAN    FALSE /* TRUE for Motorola byte order, FALSE for Intel */
#define CAM_NAME_LEN     32 /* Max length of a cam name (includes null term) */
#define PARAM_NAME_LEN   32 /* Max length of a pp param						 */

/************************ PVCAM-Specific Definitions *************************/
#define MAX_CAM          16 /* Maximum number of cameras on this system.     */

#endif /* _MASTER_H */
