///////////////////////////////////////////////////////////////////////////////
// FILE:          PI_GCS.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS Controller Driver
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 28/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
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
// CVS:           $Id: PI_GCS_2.h,v 1.7, 2010-08-31 07:35:34Z, Steffen Rau$
//

#ifndef _PI_GCS_DLL_H_
#define _PI_GCS_DLL_H_

#define PI_CNTR_NO_ERROR  0L
#define PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO 5L
#define PI_CNTR_POS_OUT_OF_LIMITS  7L

#define ERR_GCS_PI_CNTR_POS_OUT_OF_LIMITS 102
#define ERR_GCS_PI_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO 103

extern const char* g_msg_CNTR_POS_OUT_OF_LIMITS;
extern const char* g_msg_CNTR_MOVE_WITHOUT_REF_OR_NO_SERVO;

#include <string>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_OFFSET 10100

#ifndef WIN32
#define WINAPI
#define BOOL int
#define TRUE 1
#define FALSE 0
#endif

size_t ci_find(const std::string& str1, const std::string& str2);
bool GetValue(const std::string& sMessage, double& dval);
bool GetValue(const std::string& sMessage, long& lval);
std::string ExtractValue(const std::string& sMessage);

#endif //_PI_GCS_DLL_H_
