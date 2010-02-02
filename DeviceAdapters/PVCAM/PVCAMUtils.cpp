///////////////////////////////////////////////////////////////////////////////
// FILE:          PVCAMUtils.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PVCAM universal camera module
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
//
// NOTE:          The code here was copied almost verbatim from the PVCAM
//                SDK example code (Copyright Roper Scientific)
//
// CVS:           $Id$


#ifdef WIN32
#pragma warning (disable : 4800)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#ifdef WIN32
#include "Headers/master.h"
#include "Headers/pvcam.h"
#endif

#ifdef __APPLE__
#define __mac_os_x
#include <PVCAM/master.h>
#include <PVCAM/pvcam.h>
#endif

#ifdef linux
#include <pvcam/master.h>
#include <pvcam/pvcam.h>
#endif

#include "PVCAMUtils.h"
/*****************************************************************************
*
*       SetLongParam_PvCam
*
* Description :: 
*       This routine sets a parameter in PvCam, it takes care of data type for 
*   the user, by taking a long from the user.
*
*-----------------------------------------------------------------------------
*/
bool SetLongParam_PvCam
(   int16 handle,
    uns32 pvcam_cmd,
    long value
)
{
    bool status = false;
    union {
        double dtmp;
        uns8 ubytetmp;
        int16 stmp;
        uns16 ustmp;
        int32 ltmp;
        uns32 ultmp;
        uns32 etmp;
        boolean btmp;
        int8 bytetmp;
    } temp;                     /* temp variable for values, which can be any data type. */
    bool avail;                 /* PvCam available variable. */
    uns16 DataType;             /* PvCam data type variable. */
    uns16 access;               /* PvCam access variable.    */
    bool fcn_status;            /* status of function.       */
    uns32 count;                /* number of values in variable */

    fcn_status = (bool)pl_get_param( handle, pvcam_cmd, ATTR_AVAIL, (void*)&temp );
    avail = (bool)temp.btmp;

    if (fcn_status && avail)
    {
		pl_get_param( handle, pvcam_cmd, ATTR_TYPE,		
                             (void*)&DataType );
	    pl_get_param( handle, pvcam_cmd, ATTR_COUNT,    
                             (void*)&count);
		pl_get_param( handle, pvcam_cmd, ATTR_ACCESS,	
                             (void*)&access );

        /* Make sure this is a valid parameter to set. */
        if (access == ACC_READ_WRITE)
        {
            status = true;
            switch (DataType)
            {
                case TYPE_INT8 :        /* signed char                            */
                    temp.bytetmp = (char) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.bytetmp);
                    break;
                case TYPE_UNS8 :        /* unsigned char                          */
                    temp.ubytetmp = (unsigned char) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.ubytetmp);
                    break;
                case TYPE_INT16 :       /* short                                  */
                    temp.stmp = (short) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.stmp);
                    break;
                case TYPE_UNS16 :       /* unsigned short                         */
                    temp.ustmp = (unsigned short) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.ustmp);
                    break;
                case TYPE_INT32 :       /* long                                   */
                    temp.ltmp = (long) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.ltmp);
                    break;
                case TYPE_UNS32 :       /* unsigned long                          */
                    temp.ultmp = (unsigned long) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.ultmp);
                    break;
                case TYPE_FLT64 :       /* double                                 */
                    temp.dtmp = (double) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.dtmp);
                    break;
                case TYPE_BOOLEAN :     /* Boolean value                          */
                    temp.btmp = (boolean) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.btmp);
                    break;
                case TYPE_ENUM :        /* Can be treat as unsigned long          */
                    temp.ultmp = (unsigned long) value;
                    pl_set_param(handle, pvcam_cmd, (void *)&temp.ultmp);
                    break;
                default:
                /* ptrs not supported yet. */
                case TYPE_VOID_PTR :    /* ptr to void                            */
                case TYPE_VOID_PTR_PTR :/* void ptr to a ptr.                     */
                case TYPE_CHAR_PTR :    /* char                                   */
                    status = false;
                break;
            }
        }
   }

    return(status);
}                           /* end SetLongParam_PvCam */

/*****************************************************************************
*
*       GetLongParam_PvCam
*
* Description :: 
*       This routine gets a parameter in PvCam, it takes care of data type for 
*   the user by returning it in a long.
*
*-----------------------------------------------------------------------------
*/
bool GetLongParam_PvCam
(   int16 handle,
    uns32 pvcam_cmd,
    long *value
)
{
    bool status = false;
    union {
        double dtmp;
        uns8 ubytetmp;
        int16 stmp;
        uns16 ustmp;
        int32 ltmp;
        uns32 ultmp;
        uns32 etmp;
        boolean btmp;
        int8 bytetmp;
    } temp;                     /* temp variable for values, which can be any data type. */
    bool avail;                 /* PvCam available variable. */
    uns16 DataType;             /* PvCam data type variable. */
    uns16 access;               /* PvCam access variable.    */
    bool fcn_status;            /* status of function.       */
    uns32 count;                /* number of values in variable */

    fcn_status = (bool)pl_get_param( handle, pvcam_cmd, ATTR_AVAIL, (void*)&temp );
    avail = (bool)temp.btmp;

    if (fcn_status && avail)
    {
		pl_get_param( handle, pvcam_cmd, ATTR_TYPE,		
                             (void*)&DataType );
	    pl_get_param( handle, pvcam_cmd, ATTR_COUNT,    
                             (void*)&count);
		pl_get_param( handle, pvcam_cmd, ATTR_ACCESS,	
                             (void*)&access );

        /* Make sure this is a valid parameter to set. */
        if ((access == ACC_READ_WRITE) || (access == ACC_READ_ONLY))
        {
            status = true;
            switch (DataType)
            {
                case TYPE_INT8 :        /* signed char                            */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.bytetmp);
                    *value = (long) temp.bytetmp;
                    break;
                case TYPE_UNS8 :        /* unsigned char                          */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.ubytetmp);
                    *value = (long) temp.ubytetmp;
                    break;
                case TYPE_INT16 :       /* short                                  */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.stmp);
                    *value = (long) temp.stmp;
                    break;
                case TYPE_UNS16 :       /* unsigned short                         */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.ustmp);
                    *value = (long) temp.ustmp;
                    break;
                case TYPE_INT32 :       /* long                                   */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.ltmp);
                    *value = (long) temp.ltmp;
                    break;
                case TYPE_UNS32 :       /* unsigned long                          */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.ultmp);
                    *value = (long) temp.ultmp;
                    break;
                case TYPE_FLT64 :       /* double                                 */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.dtmp);
                    *value = (long) temp.dtmp;
                    break;
                case TYPE_BOOLEAN :     /* Boolean value                          */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.btmp);
                    *value = (long) temp.btmp;
                    break;
                case TYPE_ENUM :        /* Can be treat as unsigned long          */
                    pl_get_param( handle, pvcam_cmd, ATTR_CURRENT,  
                                         (void*)&temp.ultmp);
                    *value = (long) temp.ultmp;
                    break;
                default:
                /* ptrs not supported yet. */
                case TYPE_VOID_PTR :    /* ptr to void                            */
                case TYPE_VOID_PTR_PTR :/* void ptr to a ptr.                     */
                case TYPE_CHAR_PTR :    /* char                                   */
                    status = false;
                break;
            }
        }
   }

    return(status);
}   /* end GetLongParam_PvCam */


